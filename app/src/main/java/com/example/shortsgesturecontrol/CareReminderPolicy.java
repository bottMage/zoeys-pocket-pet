package com.example.shortsgesturecontrol;

import java.util.Locale;

/** Pure elapsed-time policy for low-care notifications. */
public final class CareReminderPolicy {
    public static final float LOW_THRESHOLD=20f;
    public static final float RECOVER_THRESHOLD=25f;
    public static final float CARE_AVERAGE_THRESHOLD=65f;
    public static final long CARE_RISK_FOR_MILLIS=60*60*1000L;
    public static final long REPEAT_AFTER_MILLIS=6*60*60*1000L;
    /** Care-impacting neglect gets a more useful follow-up cadence than a quiet low stat. */
    public static final long CARE_RISK_REPEAT_AFTER_MILLIS=3*60*60*1000L;
    /** Severe projected needs can worsen materially before the next normal check-in. */
    public static final long SEVERE_REPEAT_AFTER_MILLIS=90*60*1000L;
    private static final float SEVERE_NEED_THRESHOLD=10f;
    private static final float SEVERE_AVERAGE_THRESHOLD=50f;
    private static final long MAX_ELAPSED_MILLIS=3650L*PetGrowth.DAY_MILLIS;

    private CareReminderPolicy() {}

    public static final class Snapshot {
        public final boolean created,hatched,dead;
        public final float[] needs;
        public final long lastUpdate;

        public Snapshot(boolean created,boolean hatched,boolean dead,float[] needs,long lastUpdate) {
            if(needs==null||needs.length!=4)throw new IllegalArgumentException("Four needs required");
            this.created=created;this.hatched=hatched;this.dead=dead;
            this.needs=needs.clone();this.lastUpdate=lastUpdate;
        }
    }

    public static final class Evaluation {
        public final boolean shouldNotify,recovered,careRisk,urgent;
        public final int lowMask;
        public final float[] projectedNeeds;
        public final float projectedAverage;

        private Evaluation(boolean shouldNotify,boolean recovered,boolean careRisk,boolean urgent,int lowMask,
                           float[] projectedNeeds,float projectedAverage) {
            this.shouldNotify=shouldNotify;this.recovered=recovered;this.careRisk=careRisk;this.urgent=urgent;
            this.lowMask=lowMask;this.projectedNeeds=projectedNeeds;
            this.projectedAverage=projectedAverage;
        }
    }

    public static Evaluation evaluate(Snapshot snapshot,long now,long lastNotifiedAt) {
        if(!snapshot.created||snapshot.dead||snapshot.lastUpdate<=0) {
            return new Evaluation(false,true,false,false,0,new float[]{100,100,100,100},100f);
        }
        long elapsed=Math.max(0,Math.min(MAX_ELAPSED_MILLIS,now-snapshot.lastUpdate));
        float[] projected=new float[4];
        int lowMask=0;
        for(int index=0;index<4;index++) {
            float initial=clamp(snapshot.needs[index]);
            double rate=PetLife.decayPerMinute(snapshot.hatched,index);
            projected[index]=clamp((float)(initial-rate*elapsed/60000.0));
            if(projected[index]<=LOW_THRESHOLD)lowMask|=1<<index;
        }
        float average=(projected[0]+projected[1]+projected[2]+projected[3])/4f;
        double averageRate=0;
        for(int index=0;index<4;index++)averageRate+=PetLife.decayPerMinute(snapshot.hatched,index)/4.0;
        long careRiskSince=crossingTime(snapshot.lastUpdate,averageOf(snapshot.needs),CARE_AVERAGE_THRESHOLD,averageRate);
        boolean careRisk=average<CARE_AVERAGE_THRESHOLD&&now-careRiskSince>=CARE_RISK_FOR_MILLIS;
        boolean urgent=hasNeedAtOrBelow(projected,SEVERE_NEED_THRESHOLD)||average<=SEVERE_AVERAGE_THRESHOLD;
        boolean qualifies=lowMask!=0||careRisk;
        long repeatAfter=urgent?SEVERE_REPEAT_AFTER_MILLIS:
            careRisk?CARE_RISK_REPEAT_AFTER_MILLIS:REPEAT_AFTER_MILLIS;
        boolean notify=qualifies&&(lastNotifiedAt<=0||now-lastNotifiedAt>=repeatAfter);
        boolean recovered=lowMask==0&&careRisk==false&&allAbove(projected,RECOVER_THRESHOLD)&&average>=CARE_AVERAGE_THRESHOLD;
        return new Evaluation(notify,recovered,careRisk,urgent,lowMask,projected,average);
    }

    /**
     * Returns the next wall-clock threshold worth waking for, or zero if the
     * current saved state has already crossed all thresholds.
     */
    public static long nextThresholdAt(Snapshot snapshot,long now) {
        if(!snapshot.created||snapshot.dead||snapshot.lastUpdate<=0)return 0L;
        long next=Long.MAX_VALUE;
        for(int index=0;index<4;index++) {
            float initial=clamp(snapshot.needs[index]);
            double rate=PetLife.decayPerMinute(snapshot.hatched,index);
            long crossing=crossingTime(snapshot.lastUpdate,initial,LOW_THRESHOLD,rate);
            if(crossing>now&&crossing<next)next=crossing;
        }
        double averageRate=0;
        for(int index=0;index<4;index++)averageRate+=PetLife.decayPerMinute(snapshot.hatched,index)/4.0;
        long careRiskAt=crossingTime(snapshot.lastUpdate,averageOf(snapshot.needs),CARE_AVERAGE_THRESHOLD,averageRate)
            +CARE_RISK_FOR_MILLIS;
        if(careRiskAt>now&&careRiskAt<next)next=careRiskAt;
        return next==Long.MAX_VALUE?0L:next;
    }

    private static float averageOf(float[] values) {
        return (values[0]+values[1]+values[2]+values[3])/4f;
    }

    private static boolean allAbove(float[] values,float threshold) {
        for(float value:values)if(value<=threshold)return false;
        return true;
    }

    private static boolean hasNeedAtOrBelow(float[] values,float threshold) {
        for(float value:values)if(value<=threshold)return true;
        return false;
    }

    private static long crossingTime(long start,float initial,float threshold,double rate) {
        if(initial<=threshold||rate<=0)return start;
        long delay=(long)Math.ceil((initial-threshold)/rate*60000.0);
        return start+Math.max(0,delay);
    }

    private static float clamp(float value) {
        return Math.max(0f,Math.min(100f,value));
    }

    public static String percent(float value) {
        return String.format(Locale.US,"%.0f%%",value);
    }
}
