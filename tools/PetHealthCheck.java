import com.example.shortsgesturecontrol.PetLife;

/** Regression checks for elapsed-time sickness, symptom selection and medicine. */
public final class PetHealthCheck {
    private static final long HOUR=60*60*1000L;

    private static void require(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }

    public static void main(String[] args) {
        PetLife neglected=new PetLife();
        neglected.createEgg();
        neglected.hatched=true;
        neglected.needs[0]=40;neglected.needs[1]=40;neglected.needs[2]=40;neglected.needs[3]=40;
        neglected.advance(5*HOUR,5*HOUR);
        require(neglected.sick,"Sustained poor care did not make the pet sick");
        require(neglected.symptom==PetLife.TUMMY_ACHE,"Lowest need did not select the symptom");
        neglected.cure();
        require(!neglected.sick&&neglected.symptom==PetLife.NO_SYMPTOM,"Medicine did not cure the condition");

        PetLife itchy=new PetLife();
        itchy.createEgg();
        itchy.hatched=true;
        itchy.needs[3]=5;
        itchy.advance(2*HOUR,2*HOUR);
        require(itchy.sick&&itchy.symptom==PetLife.ITCHY,"Long-lasting low cleanliness symptom was not selected");
        System.out.println("PASS: elapsed sickness, symptom selection and medicine recovery");
    }
}
