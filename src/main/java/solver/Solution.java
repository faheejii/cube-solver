package solver;

import cube.Algorithm;

public class Solution {
    private Algorithm cross;
    private Algorithm f2l;
    private Algorithm oll;
    private Algorithm pll;

    public Algorithm getFullAlgorithm() {
        return cross.concat(f2l).concat(oll).concat(pll);
    }
}
