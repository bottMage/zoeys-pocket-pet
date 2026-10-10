import com.example.shortsgesturecontrol.CareReminderPolicy;

/** Regression checks for elapsed-time care reminders and cooldown behavior. */
public final class CareReminderCheck {
    private static final long HOUR=60*60*1000L;

    private static void require(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }

    private static CareReminderPolicy.Snapshot live(long lastUpdate,float hunger,float joy,float energy,float clean) {
        return new CareReminderPolicy.Snapshot(true,true,false,
            new float[]{hunger,joy,energy,clean},lastUpdate);
    }

    public static void main(String[] args) {
        long now=10*HOUR;
        CareReminderPolicy.Evaluation healthy=CareReminderPolicy.evaluate(
            live(now-2*HOUR,80,80,80,80),now,0);
        require(!healthy.shouldNotify,"Healthy pet generated a reminder");

        CareReminderPolicy.Evaluation newlyLow=CareReminderPolicy.evaluate(
            live(now-30*60*1000L,19,80,80,80),now,0);
        require(newlyLow.shouldNotify,"A stat at 20% did not generate an immediate reminder");

        long nextThreshold=CareReminderPolicy.nextThresholdAt(live(now,21,80,80,80),now);
        require(nextThreshold>now&&nextThreshold-now<=15*60*1000L,
            "The next low-stat threshold was not scheduled from elapsed time");

        CareReminderPolicy.Evaluation sustainedLow=CareReminderPolicy.evaluate(
            live(now-8*HOUR,80,80,80,80),now,0);
        require(sustainedLow.shouldNotify&&(sustainedLow.lowMask&1)!=0,
            "Sustained low hunger was not detected");
        require(!CareReminderPolicy.evaluate(live(now-8*HOUR,80,80,80,80),now,now-1*HOUR).shouldNotify,
            "Severe reminder cadence was ignored");
        require(CareReminderPolicy.evaluate(live(now-8*HOUR,80,80,80,80),now,now-2*HOUR).shouldNotify,
            "Severe care-impacting neglect did not repeat soon enough");

        CareReminderPolicy.Evaluation careRisk=CareReminderPolicy.evaluate(
            live(now-2*HOUR,60,60,60,60),now,0);
        require(careRisk.shouldNotify&&careRisk.careRisk,"Overall care risk was not detected");

        CareReminderPolicy.Evaluation recovered=CareReminderPolicy.evaluate(
            live(now-10*60*1000L,90,90,90,90),now,now-2*HOUR);
        require(recovered.recovered,"Recovered needs did not clear reminder state");
        require(!CareReminderPolicy.shouldSendGeneralReminder(true,false,now,now-90*60*1000L,0),
            "General reminder arrived before the two-hour idle period");
        require(CareReminderPolicy.shouldSendGeneralReminder(true,false,now,now-2*HOUR,0),
            "General reminder did not arrive after the idle period");
        require(!CareReminderPolicy.shouldSendGeneralReminder(true,false,now,now-3*HOUR,now-60*60*1000L),
            "General reminder ignored its repeat cadence");
        require(CareReminderPolicy.shouldSendSicknessReminder(true,now,0),
            "Sickness reminder did not send initially");
        require(!CareReminderPolicy.shouldSendSicknessReminder(true,now,now-2*HOUR),
            "Sickness reminder repeated too quickly");
        System.out.println("PASS: low threshold duration, overall care risk, cooldown, recovery and sickness reminders");
    }
}
