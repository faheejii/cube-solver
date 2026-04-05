package cube;

public class CubeState {
    public byte[] cornerPerm = new byte[8]; // which corner piece in each position
    public byte[] cornerOri = new byte[8]; // 1 of 3 orientations of the corner: 0, 1, 2
    public byte[] edgePerm = new byte[12]; // which edge piece in each position
    public byte[] edgeOri = new byte[12]; // 1 of 2 orientations of the edge: 0, 1

    public CubeState() {
        for (byte i = 0; i < cornerPerm.length; i++) {
            cornerPerm[i] = i;
            cornerOri[i] = 0;
        }

        for (byte i = 0; i < edgePerm.length; i++) {
            edgePerm[i] = i;
            edgeOri[i] = 0;
        }
    }
}
