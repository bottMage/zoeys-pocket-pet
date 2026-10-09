import com.example.shortsgesturecontrol.PetLife;
import com.example.shortsgesturecontrol.PetGrowth;
import java.util.Arrays;

/** Production clock, growth, action isolation and legacy-adult safety checks. */
public final class PetLifeCheck {
    static final long HOUR=3600000L,DAY=24*HOUR;
    static void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
    static void careAll(PetLife life) {for(int action=0;action<4;action++)life.care(action);}
    static void wellCared(PetLife life,int hours,long start) {
        for(int h=0;h<hours;h++){careAll(life);life.advance(HOUR,start+(h+1)*HOUR);}
    }
    public static void main(String[] args) {
        int[] target={0,1,3,2};
        for(int action=0;action<4;action++) {
            PetLife pet=new PetLife();pet.createEgg();Arrays.fill(pet.needs,50);
            float[] before=pet.needs.clone();pet.care(action);
            for(int i=0;i<4;i++)check(i==target[action]?pet.needs[i]>before[i]:pet.needs[i]==before[i],"Care action leaks into another need");
            check(pet.eggProgressMillis==0&&pet.evolutionMillis==0,"Action directly changes growth");
        }
        PetLife pet=new PetLife();pet.createEgg();
        wellCared(pet,47,0);check(!pet.hatched,"Egg hatches before two days");
        wellCared(pet,1,47*HOUR);check(!pet.hatched&&pet.hatchReady()&&pet.hatchEvent,"Egg did not become ready without hatching itself");
        pet.confirmHatch();check(pet.hatched&&pet.ageMillis==0,"Hatch confirmation did not start the baby stage");
        wellCared(pet,7*24-1,2*DAY);check(pet.generation==0,"Baby evolves early");
        wellCared(pet,1,9*DAY-HOUR);check(pet.generation==0&&pet.evolutionReady(),"Baby did not wait for evolution confirmation");
        pet.confirmEvolution();check(pet.generation==1,"Baby confirmation did not become young");
        wellCared(pet,7*24,9*DAY);check(pet.generation==1&&pet.evolutionReady(),"Young stage evolved without confirmation");
        pet.confirmEvolution();check(pet.generation==2&&pet.adultAgeMillis==0,"Young confirmation did not become adult");
        wellCared(pet,7*24-1,16*DAY);check(!pet.dead,"Adult dies before its own week");
        wellCared(pet,1,23*DAY-HOUR);check(!pet.dead&&pet.deathReady()&&pet.deathEvent,"Old-age prompt was skipped or killed the pet");
        pet.confirmDeath(23*DAY);check(pet.dead&&pet.diedAt==23*DAY,"Old-age confirmation did not end the pet");
        long age=pet.ageMillis;float[] finalNeeds=pet.needs.clone();pet.deathEvent=false;
        pet.advance(30*DAY,53*DAY);careAll(pet);
        check(pet.ageMillis==age&&Arrays.equals(pet.needs,finalNeeds)&&!pet.deathEvent,"Dead pet changes or repeatedly dies");
        PetLife poor=new PetLife();poor.createEgg();Arrays.fill(poor.needs,0);
        poor.advance(2*DAY,2*DAY);check(!poor.hatched&&poor.eggProgressMillis<=.101*2*DAY,"Poor egg care does not extend incubation");
        poor.hatched=true;poor.eggProgressMillis=PetGrowth.INCUBATION_MILLIS;
        poor.advance(7*DAY,9*DAY);check(poor.generation==0&&poor.evolutionMillis<=.101*7*DAY,"Poor care does not slow evolution");
        double earned=poor.evolutionMillis;poor.care(0);check(poor.evolutionMillis==earned,"Care erases or instantly grants growth");
        PetLife legacy=new PetLife();legacy.created=true;legacy.hatched=true;legacy.generation=2;
        legacy.ageMillis=100*DAY;legacy.adultClockReady=false;
        legacy.advance(100*DAY,200*DAY);check(!legacy.dead&&legacy.adultAgeMillis==0,"Legacy adult suddenly dies on upgrade/restore");
        legacy.advance(HOUR,200*DAY+HOUR);check(legacy.adultAgeMillis==HOUR,"Legacy death clock never starts");
        PetLife forever=new PetLife();forever.created=true;forever.hatched=true;forever.generation=2;
        forever.adultAgeMillis=PetGrowth.ADULT_LIFESPAN_MILLIS;check(forever.deathReady(),"Old-age choice did not become available");
        forever.keepForever();forever.advance(30*DAY,30*DAY);check(!forever.dead&&forever.oldAgeDeclined,"Keep-forever choice did not persist");
        PetLife eggOvernight=new PetLife();eggOvernight.createEgg();eggOvernight.advance(9*HOUR,9*HOUR);
        check(eggOvernight.needs[0]<82-20&&eggOvernight.needs[0]>82-24,"Egg overnight decay is not moderate");
        check(eggOvernight.eggProgressMillis>8*HOUR&&eggOvernight.eggProgressMillis<10*HOUR,"Egg clock does not catch up offline");
        PetLife schoolDay=new PetLife();schoolDay.createEgg();schoolDay.hatched=true;schoolDay.advance(6*HOUR,6*HOUR);
        check(schoolDay.needs[0]>30&&schoolDay.needs[0]<35,"Live school-day hunger decay is too fast or slow");
        Arrays.fill(schoolDay.needs,0);double slowBefore=schoolDay.evolutionMillis;
        schoolDay.advance(7*DAY,7*DAY);
        check(schoolDay.needsCritical()&&schoolDay.evolutionMillis>slowBefore,"Drained pet must remain alive and grow slowly");
        check(Math.abs(PetGrowth.migrateEvolution(0,6*HOUR,100)-PetGrowth.STAGE_MILLIS*.5)<1,"Legacy baby progress lost");
        check(Math.abs(PetGrowth.migrateEvolution(1,36*HOUR,100)-PetGrowth.STAGE_MILLIS*.5)<1,"Legacy young progress lost");
        check(PetGrowth.stage(true,1)==PetGrowth.Stage.YOUNG&&PetGrowth.stage(true,2)==PetGrowth.Stage.ADULT,"Existing generation regresses");
        for(String kind:new String[]{"cat","dog","bunny","hamster","dragon"}) {
            float[] identity=PetGrowth.colorMatrix(kind,PetGrowth.Stage.BABY);
            check(Math.abs(identity[0]-1)<1e-6&&Math.abs(identity[6]-1)<1e-6&&Math.abs(identity[12]-1)<1e-6,"Baby design is recoloured");
            for(PetGrowth.Stage stage:PetGrowth.Stage.values())for(float value:PetGrowth.colorMatrix(kind,stage))
                check(Float.isFinite(value),"Invalid coat colour matrix");
        }
        System.out.println("PASS: two-day egg; seven-day baby/young/adult; slower poor-care growth; isolated actions; offline aging; one-shot old age; legacy safety and progress; unchanged baby palette.");
    }
}
