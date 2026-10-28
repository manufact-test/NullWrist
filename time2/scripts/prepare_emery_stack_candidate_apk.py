#!/usr/bin/env python3
"""Create an UNSIGNED device-lab APK with experimental 4 KiB legacy 3.x stack.

Input base APK must be a valid official-fixed PebbleOS Time2 lab assembly.
Only the guest firmware ELF and its manifest/provenance change; the bundled
SPI flash and Android app package/user data are never modified.
Use the separate *private* signer to attach owner's PBWs and sign this ZIP.
DO NOT ship as a verified fix without on-device Pixel Miner fault regression.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import struct
import zipfile

OFFICIAL_ELF_SHA = "82c040d2ec3fe98963289aca07c31493e56d9ee6a323d07f3179cd17d65b4295"
CANDIDATE_ELF_SHA = "cd096cc168aced92bd06da9bea656c0df0c6504a7f75cd3630bfdb3eec568de7"
OFFICIAL_SPI_SHA = "94bd2ced51295050af0fd401863495680e0e7fcf10d0ff3c308611fb4cd86312"
RUN_ID = 40244566798

def sha(data):
    return hashlib.sha256(data).hexdigest()

def strip_apk_v2(data):
    marker=b"APK Sig Block 42"
    pos=data.rfind(b"PK\x05\x06",max(0,len(data)-65557))
    if pos<0 or pos+22>len(data):raise ValueError("Malformed APK EOCD")
    comment=struct.unpack_from("<H",data,pos+20)[0]
    if pos+22+comment != len(data):raise ValueError("APK EOCD comment invalid")
    cd_offset=struct.unpack_from("<I",data,pos+16)[0]
    if data[cd_offset-16:cd_offset]!=marker:
        return data
    size=struct.unpack_from("<Q",data,cd_offset-24)[0]
    start=cd_offset-size-8
    if start<0 or struct.unpack_from("<Q",data,start)[0]!=size:
        raise ValueError("Invalid v2 signing block")
    footer=bytearray(data[pos:])
    struct.pack_into("<I",footer,16,start)
    return data[:start]+data[cd_offset:pos]+footer

def prepare(base,source,out):
    candidate=source.read_bytes()
    if sha(candidate)!=CANDIDATE_ELF_SHA:
        raise ValueError("Candidate firmware SHA256 does not match source-only 4KiB build")
    unsigned=strip_apk_v2(base.read_bytes())
    firmware="assets/emery/pebbleos.elf"
    spi="assets/emery/qemu_spi_flash.bin"
    sums="assets/emery/SHA256SUMS"
    provenance="assets/emery/source-provenance.json"
    with zipfile.ZipFile(io.BytesIO(unsigned)) as z:
        entries={item.filename:z.read(item) for item in z.infolist()}
        infos={item.filename:item for item in z.infolist()}
    if sha(entries[firmware])!=OFFICIAL_ELF_SHA:
        raise ValueError("Unexpected base firmware, refusing candidate conversion")
    if sha(entries[spi])!=OFFICIAL_SPI_SHA:
        raise ValueError("Unexpected bundled SPI: refusing replacement")
    entries[firmware]=candidate
    lines=entries[sums].decode().splitlines()
    converted=[]
    for line in lines:
        fields=line.split()
        if len(fields)==2 and fields[1]=="pebbleos.elf":
            if fields[0]!=OFFICIAL_ELF_SHA:raise ValueError("Firmware manifest mismatch")
            line=CANDIDATE_ELF_SHA+"  pebbleos.elf"
        converted.append(line)
    entries[sums]=("\n".join(converted)+"\n").encode()
    meta=json.loads(entries[provenance])
    meta.update(firmware_sha256=CANDIDATE_ELF_SHA,
                variant="EXPERIMENTAL emery legacy 3.x 4KiB app stack, not a production fix",
                candidate_source_run=RUN_ID,mpu="unchanged",
                user_spi_storage="must preserve existing Android private qemu_spi_flash.bin")
    entries[provenance]=(json.dumps(meta,indent=2,sort_keys=True)+"\n").encode()
    out.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(out,"w",allowZip64=False) as target:
        for filename,body in entries.items():
            info=infos[filename]
            target.writestr(info,body)
    with zipfile.ZipFile(out) as verify:
        if verify.testzip() is not None:raise ValueError("APK ZIP integrity failure")
        if sha(verify.read(firmware))!=CANDIDATE_ELF_SHA:raise ValueError("Wrong ELF in output")
        if sha(verify.read(spi))!=OFFICIAL_SPI_SHA:raise ValueError("SPI was modified")
    print("UNSIGNED experimental 4KiB candidate:",out)
    print("SHA-256:",sha(out.read_bytes()))

if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-apk",type=Path,required=True)
    parser.add_argument("--candidate-elf",type=Path,required=True)
    parser.add_argument("--output",type=Path,required=True)
    args=parser.parse_args()
    prepare(args.base_apk,args.candidate_elf,args.output)
