package com.manufact.pixelfoldclosed;
interface IFoldControl {
    // 0 = force CLOSED base state, 1 = reset, 2 = print diagnostics.
    String execute(int action);
}
