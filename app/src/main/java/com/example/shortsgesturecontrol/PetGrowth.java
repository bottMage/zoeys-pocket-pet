package com.example.shortsgesturecontrol;

/** Growth rules shared by rendering, incubation and the offline review. */
public final class PetGrowth {
    public static final long DAY_MILLIS=24*60*60*1000L;
    public static final long INCUBATION_MILLIS=2*DAY_MILLIS;
    public static final long STAGE_MILLIS=7*DAY_MILLIS;
    public static final long ADULT_LIFESPAN_MILLIS=7*DAY_MILLIS;
    private PetGrowth() {}
    public enum Stage {
        EGG(0),BABY(.55),YOUNG(.77),ADULT(1);
        public final double size;
        Stage(double size){this.size=size;}
    }
    public static Stage stage(boolean hatched,int generation) {
        return !hatched?Stage.EGG:generation>=2?Stage.ADULT:generation==1?Stage.YOUNG:Stage.BABY;
    }
    public static double hatchProgress(long eggAgeMillis) {
        return Math.max(0,Math.min(1,(double)eggAgeMillis/INCUBATION_MILLIS));
    }
    public static double careSpeed(double care) {return Math.max(.1,Math.min(1,care/75));}
    public static double migrateEvolution(int generation,long ageMillis,double carePercent) {
        if(generation>=2)return 0;
        double ageTarget=(generation==0?12:72)*60*60*1000.0;
        double careTarget=generation==0?55:72;
        return STAGE_MILLIS*Math.max(0,Math.min(1,Math.min(ageMillis/ageTarget,carePercent/careTarget)));
    }
    public static boolean oldAge(long adultAgeMillis) {return adultAgeMillis>=ADULT_LIFESPAN_MILLIS;}
    public static double eggAngle(double seconds,double progress) {
        // Slow resting sway, with a brief stronger wobble before hatching.
        double pulse=Math.pow(Math.max(0,Math.sin(seconds*.7)),6);
        return Math.sin(seconds*1.8)*(2.2+2.8*pulse+1.5*progress);
    }
    public static double eggLift(double seconds) {
        return -1.4*Math.pow(Math.max(0,Math.sin(seconds*.7)),8);
    }
    public static float[] colorMatrix(String kind,Stage stage) {
        double amount=stage==Stage.ADULT?1:stage==Stage.YOUNG?.45:0;
        double hue;
        switch(kind){case "cat":hue=-7;break;case "dog":hue=-4;break;
            case "bunny":hue=9;break;case "hamster":hue=-3;break;default:hue=7;}
        double angle=Math.toRadians(hue*amount),c=Math.cos(angle),s=Math.sin(angle);
        double[][] rotation={
            {.213+.787*c-.213*s,.715-.715*c-.715*s,.072-.072*c+.928*s},
            {.213-.213*c+.143*s,.715+.285*c+.140*s,.072-.072*c-.283*s},
            {.213-.213*c-.787*s,.715-.715*c+.715*s,.072+.928*c+.072*s}};
        double saturation=1+.10*amount,contrast=1+.09*amount;
        double[] luminance={.213,.715,.072};float[] matrix=new float[20];
        for(int row=0;row<3;row++) {
            for(int col=0;col<3;col++) {
                double value=0;
                for(int k=0;k<3;k++)value+=rotation[row][k]*((1-saturation)*luminance[col]+(k==col?saturation:0));
                matrix[row*5+col]=(float)(value*contrast);
            }
            matrix[row*5+4]=(float)(128*(1-contrast));
        }
        matrix[18]=1;
        return matrix;
    }
}
