#!/usr/bin/env python3
"""Repair the kernel-memory read after CONTROL.nPRIV in the pinned Emery ELF.

The exact original emulator ELF is matched by SHA256. The generated trampoline
reads s_app_stack_top while privileged, then restores task_init's original
semantics. It never changes the MPU, application payload, or persistent SPI.
Fails closed for any other build or unexpected executable layout.
"""
import argparse
import hashlib
import struct
from pathlib import Path

ORIGINAL_SHA256 = "6f9a5511ca0886cd56aa8b017436c2db5ea5f426c599f8f01029a000a98e68c0"
CALL_SITE = 0x6ED48
POST_MODE_SITE = 0x6ED62
CODE_REGION = 0x1BC000
TASK_INIT = 0x10C8A6
STACK_TOP_GLOBAL = 0x20012D7C

def thumb_bl(src, dst):
    delta = dst - src - 4
    if delta % 2 or not -(1 << 24) <= delta < (1 << 24):
        raise ValueError("Thumb BL branch target out of range")
    imm = delta & ((1 << 25) - 1)
    s, i1, i2 = (imm >> 24) & 1, (imm >> 23) & 1, (imm >> 22) & 1
    j1, j2 = 1 ^ i1 ^ s, 1 ^ i2 ^ s
    return struct.pack("<HH",
        0xF000 | (s << 10) | ((imm >> 12) & 0x3FF),
        0xD000 | (j1 << 13) | (j2 << 11) | ((imm >> 1) & 0x7FF))

def patch(path):
    data = bytearray(Path(path).read_bytes())
    actual = hashlib.sha256(data).hexdigest()
    if actual != ORIGINAL_SHA256:
        raise ValueError("Unknown PebbleOS build; refusing patch: " + actual)
    if data[:7] != b"\x7fELF\x01\x01\x01" or struct.unpack_from("<H",data,18)[0] != 40:
        raise ValueError("Expected ARM ELF32 little-endian")
    phoff = struct.unpack_from("<I", data, 28)[0]
    shoff = struct.unpack_from("<I", data, 32)[0]
    ehsize, phentsize, phnum = struct.unpack_from("<HHH",data,40)
    if (phoff,ehsize,phentsize,phnum) != (52,52,32,10):
        raise ValueError("Unknown ELF program-header layout")
    if any(data[phoff+phnum*phentsize:phoff+(phnum+1)*phentsize]):
        raise ValueError("No space for new program header")
    entries = [struct.unpack_from("<IIIIIIII",data,phoff+i*phentsize) for i in range(phnum)]
    if entries[0][:4] != (1,0x1000,0,0) or entries[0][6:] != (5,0x1000):
        raise ValueError("Unexpected firmware executable segment")
    for typ, _, _, physical, filesz, _, _, _ in entries:
        if typ == 1 and filesz and max(physical,CODE_REGION)<min(physical+filesz,CODE_REGION+16):
            raise ValueError("Trampoline overlaps existing firmware")
    def at(addr):
        return 0x1000+addr
    if data[at(CALL_SITE):at(CALL_SITE)+4] != bytes.fromhex("9d f0 ad fd"):
        raise ValueError("Original task_init BL does not match")
    if data[at(POST_MODE_SITE):at(POST_MODE_SITE)+2] != bytes.fromhex("1b 68"):
        raise ValueError("Original unprivileged kernel read does not match")
    if struct.unpack_from("<I",data,at(0x6ED7C))[0] != STACK_TOP_GLOBAL:
        raise ValueError("Kernel variable address does not match")
    # Preserve stack 8-byte alignment across the wrapper call.
    # push {r3,lr}; bl task_init; ldr r6,[pc,#4]; ldr r6,[r6];
    # pop {r3,pc}; literal address of s_app_stack_top.
    thunk = (struct.pack("<H",0xB508)+thumb_bl(CODE_REGION+2,TASK_INIT)
             +struct.pack("<HHH",0x4E01,0x6836,0xBD08)
             +struct.pack("<I",STACK_TOP_GLOBAL))
    assert len(thunk)==16
    data[at(CALL_SITE):at(CALL_SITE)+4]=thumb_bl(CALL_SITE,CODE_REGION)
    data[at(POST_MODE_SITE):at(POST_MODE_SITE)+2]=struct.pack("<H",0x4633) # mov r3,r6
    # Independent RX flash PT_LOAD; do not overlap or replace the data/SPI
    # sections, and do not weaken the unprivileged app MPU settings.
    code_file_offset=(len(data)+0xfff)&~0xfff
    data.extend(b"\x00"*(code_file_offset-len(data)))
    data.extend(thunk)
    struct.pack_into("<IIIIIIII",data,phoff+phnum*phentsize,1,
                     code_file_offset,CODE_REGION,CODE_REGION,16,16,5,0x1000)
    struct.pack_into("<H",data,44,phnum+1)
    if not (shoff < code_file_offset):
        raise ValueError("Unexpected section header location")
    if data[code_file_offset:code_file_offset+16]!=thunk:
        raise ValueError("Trampoline verification failed")
    Path(path).write_bytes(data)
    print("Patched known Emery app-entry fault: "+hashlib.sha256(data).hexdigest())

if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("elf",type=Path)
    patch(parser.parse_args().elf)
