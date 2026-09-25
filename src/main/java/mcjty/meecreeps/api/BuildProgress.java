package mcjty.meecreeps.api;

public class BuildProgress {
    private int pass = 0;
    private int maxpasses;
    private int height = 0;

    public BuildProgress(int maxpasses, int height) {
        this.maxpasses = maxpasses;
        this.height = height;
        pass = 0;
    }

    public int getPass() {
        return pass;
    }

    public int getHeight() {
        return height;
    }

    public void setPass(int pass) {
        this.pass = pass;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public boolean next(IBuildSchematic schematic) {
        pass++;
        if (pass >= maxpasses) {
            pass = 0;
            height++;
            if (height > schematic.getMaxPos().getY()) {
                return false;
            }
        }
        return true;
    }
}
