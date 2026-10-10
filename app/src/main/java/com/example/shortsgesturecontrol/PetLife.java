package com.example.shortsgesturecontrol;

/** Clock/care engine; contains no Android, storage or renderer dependencies. */
public final class PetLife {
    public final float[] needs={78,82,74,88};
    public boolean created,hatched,dead,hatchEvent,evolutionEvent,deathEvent,adultClockReady=true;
    /** Once old age is reached, the player can choose to keep this pet forever. */
    public boolean oldAgeDeclined;
    public int generation;
    public long ageMillis,eggAgeMillis,adultAgeMillis,goodCareMillis,totalCareMillis,diedAt;
    public double eggProgressMillis,evolutionMillis;
    private static final long HOUR=60*60*1000L;
    private static final float[] BOOST={18,16,22,25};
    private static final int[] NEED_FOR_ACTION={0,1,3,2};
    // Rates are points lost per minute while the app is away. A six-hour
    // school day costs about 50/32/40/22 points for a live pet, while a
    // healthy egg loses about 22/14/16/10 points over nine hours. That makes
    // care a recurring part of the day without making neglect lethal.
    private static final double[] LIVE_DECAY={.14,.090,.110,.060};
    private static final double[] EGG_DECAY={.040,.025,.030,.018};

    public double average() {return (needs[0]+needs[1]+needs[2]+needs[3])/4.0;}
    public double carePercent() {return totalCareMillis==0?0:100.0*goodCareMillis/totalCareMillis;}
    public boolean needsCritical() {
        for(float need:needs)if(need<=0.01f)return true;
        return false;
    }

    /** Points lost per minute for a need while the pet is away. */
    public static double decayPerMinute(boolean hatched,int needIndex) {
        if(needIndex<0||needIndex>=4)throw new IllegalArgumentException("Unknown need index");
        return (hatched?LIVE_DECAY:EGG_DECAY)[needIndex];
    }

    public void createEgg() {
        created=true;hatched=false;dead=false;generation=0;
        ageMillis=eggAgeMillis=adultAgeMillis=goodCareMillis=totalCareMillis=diedAt=0;
        eggProgressMillis=evolutionMillis=0;adultClockReady=true;
        hatchEvent=evolutionEvent=deathEvent=false;oldAgeDeclined=false;
        needs[0]=82;needs[1]=88;needs[2]=84;needs[3]=92;
    }

    public boolean hatchReady() {
        return created&&!hatched&&!dead&&eggProgressMillis>=PetGrowth.INCUBATION_MILLIS;
    }

    public boolean evolutionReady() {
        return created&&hatched&&!dead&&generation<2&&evolutionMillis>=PetGrowth.STAGE_MILLIS;
    }

    public boolean deathReady() {
        return created&&hatched&&!dead&&generation>=2&&!oldAgeDeclined&&PetGrowth.oldAge(adultAgeMillis);
    }

    public void confirmHatch() {
        if(!hatchReady())return;
        hatched=true;hatchEvent=false;ageMillis=0;
        goodCareMillis=totalCareMillis=0;evolutionMillis=0;adultAgeMillis=0;
        needs[0]=82;needs[1]=88;needs[2]=84;needs[3]=92;
    }

    public void confirmEvolution() {
        if(!evolutionReady())return;
        evolutionMillis=0;generation++;evolutionEvent=false;
        if(generation>=2)adultAgeMillis=0;
    }

    public void confirmDeath(long now) {
        if(!deathReady())return;
        dead=true;deathEvent=false;diedAt=now;
    }

    public void keepForever() {
        if(!deathReady())return;
        oldAgeDeclined=true;deathEvent=false;
    }

    public void care(int action) {
        if(action<0||action>=BOOST.length)throw new IllegalArgumentException("Unknown care action");
        if(!created||dead)return;
        int need=NEED_FOR_ACTION[action];
        // Exactly one metric changes. Feeding never affects play or rest, etc.
        needs[need]=Math.min(100,needs[need]+BOOST[action]);
    }

    public void advance(long elapsedMillis,long now) {
        if(!created||dead||elapsedMillis<=0)return;
        // A legacy adult gets a full new adult lifespan; never backdate the
        // newly introduced death clock over time spent on an older APK.
        boolean skipAdultTime=!adultClockReady;adultClockReady=true;
        long remaining=Math.min(elapsedMillis,3650*PetGrowth.DAY_MILLIS);
        long clock=now-remaining;
        while(remaining>0&&!dead) {
            long step=Math.min(HOUR,remaining);
            double before=average();boolean liveAtStart=hatched;
            double[] decay=hatched?LIVE_DECAY:EGG_DECAY;
            for(int i=0;i<needs.length;i++)needs[i]=(float)Math.max(0,needs[i]-step/60000.0*decay[i]);
            double after=average();
            totalCareMillis+=step;
            if((before+after)*.5>=65)goodCareMillis+=step;
            double growing=step*(PetGrowth.careSpeed(before)+PetGrowth.careSpeed(after))*.5;
            if(!liveAtStart) {
                eggAgeMillis+=step;eggProgressMillis+=growing;
                if(eggProgressMillis>=PetGrowth.INCUBATION_MILLIS) {
                    eggProgressMillis=PetGrowth.INCUBATION_MILLIS;hatchEvent=true;
                }
            } else {
                ageMillis+=step;
                if(generation<2) {
                    evolutionMillis=Math.min(PetGrowth.STAGE_MILLIS,evolutionMillis+growing);
                    if(evolutionMillis>=PetGrowth.STAGE_MILLIS) {
                        evolutionMillis=PetGrowth.STAGE_MILLIS;evolutionEvent=true;
                    }
                } else if(!skipAdultTime&&!oldAgeDeclined) {
                    adultAgeMillis=Math.min(PetGrowth.ADULT_LIFESPAN_MILLIS,adultAgeMillis+step);
                    if(PetGrowth.oldAge(adultAgeMillis)) {
                        adultAgeMillis=PetGrowth.ADULT_LIFESPAN_MILLIS;deathEvent=true;
                    }
                }
            }
            clock+=step;remaining-=step;
        }
    }
}
