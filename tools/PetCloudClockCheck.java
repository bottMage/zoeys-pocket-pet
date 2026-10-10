import java.util.*;
import com.example.shortsgesturecontrol.PetLife;
import com.example.shortsgesturecontrol.PetGrowth;

/** Regression checks against the real compiled Kotlin cloud-restore model.
 * Run alongside PetSaveCheck with its Color stub, Android API, Kotlin and JSON jars.
 */
public final class PetCloudClockCheck {
    static final long MINUTE=60000L, HOUR=60*MINUTE, NOW=1800000000000L;
    static Object restore(Object state,Map<String,Object> data,long now)throws Exception {
        PetSaveCheck.call(state,"restoreCloud",new Class[]{Map.class,long.class},data,now);
        return state;
    }
    static Map<String,Object> snapshot(boolean hatched,long at)throws Exception {
        Object state=PetSaveCheck.create(new PetSaveCheck.Preferences());
        PetLife life=PetSaveCheck.life(state);life.createEgg();life.hatched=hatched;
        life.eggAgeMillis=23*MINUTE;life.eggProgressMillis=23*MINUTE;
        life.ageMillis=hatched?3*HOUR:0;life.evolutionMillis=hatched?HOUR:0;
        Map<String,Object> data=new HashMap<>(PetSaveCheck.cloud(state));
        data.put("lastUpdate",at);data.put("savedAt",at);
        data.put("name","Drago");data.put("kind","DRAGON");
        return data;
    }
    static void check(boolean condition,String message){PetSaveCheck.check(condition,message);}
    static void expected(PetLife actual,PetLife expected,String label) {
        check(actual.eggAgeMillis==expected.eggAgeMillis&&actual.ageMillis==expected.ageMillis,label+": age");
        check(Math.abs(actual.eggProgressMillis-expected.eggProgressMillis)<1,label+": incubation");
        check(Math.abs(actual.evolutionMillis-expected.evolutionMillis)<1,label+": evolution");
        check(Arrays.equals(actual.needs,expected.needs),label+": needs");
        check(actual.totalCareMillis==expected.totalCareMillis&&actual.goodCareMillis==expected.goodCareMillis,label+": care");
        check(actual.sick==expected.sick&&actual.symptom==expected.symptom&&actual.sickAt==expected.sickAt,label+": illness");
        check(actual.poorCareMillis==expected.poorCareMillis&&actual.lowNeedMillis==expected.lowNeedMillis,label+": neglect");
        check(actual.adultAgeMillis==expected.adultAgeMillis&&actual.dead==expected.dead,label+": old age");
    }
    public static void main(String[] args)throws Exception {
        PetSaveCheck.stateClass=Class.forName("com.example.shortsgesturecontrol.PetGameView$PetState");
        for(boolean hatched:new boolean[]{false,true}) {
            Map<String,Object> cloud=snapshot(hatched,NOW-2*HOUR);
            Object control=PetSaveCheck.create(new PetSaveCheck.Preferences());
            PetSaveCheck.call(control,"loadCloud",new Class[]{Map.class},cloud);
            PetSaveCheck.life(control).advance(2*HOUR,NOW);
            PetSaveCheck.Preferences stale=new PetSaveCheck.Preferences();
            stale.data.put("last_update",NOW-30*PetGrowth.DAY_MILLIS);
            stale.data.put("saved_at",NOW+HOUR); // even a newer stale local save cannot win.
            Object device=restore(PetSaveCheck.create(stale),cloud,NOW);
            expected(PetSaveCheck.life(device),PetSaveCheck.life(control),"two hours closed");
            check(((Number)PetSaveCheck.cloud(device).get("lastUpdate")).longValue()==NOW,"Catch-up timestamp not persisted");
            check(PetSaveCheck.call(device,"getName").equals("Drago"),"Identity lost");
            if(!hatched) {
                check(PetSaveCheck.life(device).eggAgeMillis==143*MINUTE,"23-minute egg must become 143 minutes");
                check(PetSaveCheck.life(device).eggProgressMillis>23*MINUTE,"Incubation frozen");
            } else check(PetSaveCheck.life(device).ageMillis==5*HOUR,"Live age frozen");
            String[] needs={"hunger","joy","energy","clean"};
            for(int i=0;i<needs.length;i++)check(PetSaveCheck.life(device).needs[i]<((Number)cloud.get(needs[i])).floatValue(),"Need decay missing: "+needs[i]);
            restore(device,cloud,NOW);
            expected(PetSaveCheck.life(device),PetSaveCheck.life(control),"same snapshot cannot double-count");
            restore(device,cloud,NOW+1800);
            PetSaveCheck.life(control).advance(1800,NOW+1800);
            expected(PetSaveCheck.life(device),PetSaveCheck.life(control),"resume verification");
            Object second=restore(PetSaveCheck.create(new PetSaveCheck.Preferences()),cloud,NOW+1800);
            expected(PetSaveCheck.life(second),PetSaveCheck.life(device),"two devices at same wall time");
            // Save/recreate exactly as process death does; lastUpdate stays caught up.
            PetSaveCheck.call(device,"save");
            Object reboot=PetSaveCheck.create(stale);
            expected(PetSaveCheck.life(reboot),PetSaveCheck.life(device),"local save/restart");
            restore(second,PetSaveCheck.cloud(reboot),NOW+1800);
            expected(PetSaveCheck.life(second),PetSaveCheck.life(reboot),"caught-up snapshot restored without extra decay");
            Map<String,Object> unhealthy=snapshot(hatched,NOW-6*HOUR);
            for(String need:new String[]{"hunger","joy","energy","clean"})unhealthy.put(need,5.0);
            restore(device,unhealthy,NOW);
            check(PetSaveCheck.life(device).sick&&PetSaveCheck.life(device).sickAt>0,"Offline neglect/illness frozen");
        }
        Object state=PetSaveCheck.create(new PetSaveCheck.Preferences());
        Map<String,Object> nearHatch=snapshot(false,NOW-2*HOUR);
        nearHatch.put("eggProgressMillis",PetGrowth.INCUBATION_MILLIS-HOUR);
        restore(state,nearHatch,NOW);
        check(PetSaveCheck.life(state).hatchReady()&&!PetSaveCheck.life(state).hatched,"Offline hatch readiness/consent broken");
        Map<String,Object> nearEvolution=snapshot(true,NOW-2*HOUR);
        nearEvolution.put("evolutionMillis",PetGrowth.STAGE_MILLIS-HOUR);
        restore(state,nearEvolution,NOW);
        check(PetSaveCheck.life(state).evolutionReady()&&PetSaveCheck.life(state).generation==0,"Offline evolution readiness/consent broken");
        Map<String,Object> adult=snapshot(true,NOW-8*PetGrowth.DAY_MILLIS);adult.put("generation",2L);
        restore(state,adult,NOW);
        check(PetSaveCheck.life(state).deathReady()&&!PetSaveCheck.life(state).dead,"Offline old-age readiness/consent broken");
        restore(state,snapshot(false,NOW),NOW);
        check(!PetSaveCheck.life(state).hatched&&PetSaveCheck.life(state).eggAgeMillis==23*MINUTE,"Authoritative reset egg rejected for older local adult");
        restore(state,snapshot(false,NOW+HOUR),NOW);
        check(PetSaveCheck.life(state).eggAgeMillis==23*MINUTE,"Future timestamp creates negative elapsed time");
        Map<String,Object> dead=snapshot(true,NOW-HOUR);dead.put("dead",true);
        restore(state,dead,NOW);
        check(PetSaveCheck.life(state).ageMillis==3*HOUR,"Deceased pet must not continue aging");
        // Signed-out/offline startup still catches up its persisted local clock.
        PetSaveCheck.Preferences local=new PetSaveCheck.Preferences();
        long wallNow=System.currentTimeMillis();
        PetSaveCheck.call(state,"loadCloud",new Class[]{Map.class},snapshot(false,wallNow-10*MINUTE));
        // Reuse the same preferences as the object, then reload after saving.
        Object offline=PetSaveCheck.create(local);
        PetSaveCheck.call(offline,"loadCloud",new Class[]{Map.class},PetSaveCheck.cloud(state));
        PetSaveCheck.call(offline,"save");offline=PetSaveCheck.create(local);
        PetSaveCheck.call(offline,"updateFromClock");
        long age=PetSaveCheck.life(offline).eggAgeMillis;
        check(age>=33*MINUTE&&age<34*MINUTE,"Local-only restart no longer advances time");
        long after=PetSaveCheck.life(offline).eggAgeMillis;
        PetSaveCheck.call(offline,"updateFromClock");
        check(PetSaveCheck.life(offline).eggAgeMillis-after<MINUTE,"Local draw double-counts elapsed time");
        System.out.println("PASS: actual Kotlin cloud restore; closed egg/live aging, incubation/evolution, every need, health/neglect, adult readiness; stale local clock ignored; repeat restore and two devices; restart; reset authority; clock skew; deceased pet safety.");
    }
}
