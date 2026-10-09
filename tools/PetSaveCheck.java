import java.lang.reflect.*;
import java.util.*;
import android.content.SharedPreferences;
import com.example.shortsgesturecontrol.PetLife;
import org.json.JSONArray;

/** Calls the actual compiled Kotlin PetState with in-memory Android preferences. */
public final class PetSaveCheck {
    static Class<?> stateClass;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static final class Preferences implements InvocationHandler {
        final Map<String,Object> data=new HashMap<>();
        final SharedPreferences prefs=(SharedPreferences)Proxy.newProxyInstance(Preferences.class.getClassLoader(),new Class[]{SharedPreferences.class},this);
        public Object invoke(Object proxy,Method method,Object[] args) {
            String name=method.getName();
            if(name.equals("contains"))return data.containsKey(args[0]);
            if(name.equals("getAll"))return new HashMap<>(data);
            if(name.equals("edit")) {
                Map<String,Object> pending=new HashMap<>();boolean[] clear={false};
                return Proxy.newProxyInstance(Preferences.class.getClassLoader(),new Class[]{SharedPreferences.Editor.class},(editor,m,a)->{
                    if(m.getName().equals("clear")){clear[0]=true;return editor;}
                    if(m.getName().equals("apply")||m.getName().equals("commit")){
                        if(clear[0])data.clear();data.putAll(pending);return m.getName().equals("commit")?true:null;
                    }
                    if(m.getName().startsWith("put")){pending.put((String)a[0],a[1]);return editor;}
                    throw new AssertionError("Unexpected editor method "+m);
                });
            }
            if(name.startsWith("get")) {
                Object result=data.getOrDefault(args[0],args[1]);
                if(name.equals("getFloat")&&!(result instanceof Float))throw new ClassCastException();
                return result;
            }
            throw new AssertionError("Unexpected preference method "+method);
        }
    }
    static Object create(Preferences prefs)throws Exception {
        Constructor<?> c=stateClass.getDeclaredConstructor(SharedPreferences.class);c.setAccessible(true);return c.newInstance(prefs.prefs);
    }
    static Object call(Object state,String name,Class<?>[] types,Object...args)throws Exception {
        Method m=stateClass.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(state,args);
    }
    static Object call(Object state,String name)throws Exception{return call(state,name,new Class[0]);}
    static PetLife life(Object state)throws Exception {Field f=stateClass.getDeclaredField("life");f.setAccessible(true);return (PetLife)f.get(state);}
    @SuppressWarnings("unchecked") static Map<String,Object> cloud(Object state)throws Exception{return (Map<String,Object>)call(state,"cloudData");}
    static Map<String,Object> oldCloud(long now) {
        Map<String,Object> data=new HashMap<>();
        data.put("created",true);data.put("hatched",true);data.put("kind","CAT");data.put("name","Zoey's cat");
        data.put("generation",2L);data.put("ageMillis",100*86400000L);data.put("goodCareMillis",50*86400000L);
        data.put("totalCareMillis",100*86400000L);data.put("lastUpdate",now-100*86400000L);
        data.put("savedAt",now-1000);data.put("hunger",82.0);data.put("joy",88.0);data.put("energy",84.0);data.put("clean",92.0);
        return data;
    }
    public static void main(String[] args)throws Exception {
        stateClass=Class.forName("com.example.shortsgesturecontrol.PetGameView$PetState");
        long now=System.currentTimeMillis();Preferences prefs=new Preferences();
        prefs.data.put("hunger",60); // original older APK integer metric.
        Object pet=create(prefs);check((Float)call(pet,"getHunger")==60,"Legacy integer hunger lost");
        call(pet,"loadCloud",new Class[]{Map.class},oldCloud(now));
        call(pet,"save"); // Simulate process death before the first draw after restoring.
        pet=create(prefs);call(pet,"updateFromClock");
        check(!life(pet).dead&&life(pet).adultAgeMillis==0,"Legacy adult dies on reinstall/upgrade");
        check(life(pet).generation==2&&call(pet,"getName").equals("Zoey's cat"),"Existing generation/name lost");
        Map<String,Object> saved=cloud(pet);
        for(String key:oldCloud(now).keySet())check(saved.containsKey(key),"Existing cloud key removed: "+key);
        Class<?> kind=Class.forName("com.example.shortsgesturecontrol.PetGameView$PetKind");
        Object dog=Arrays.stream(kind.getEnumConstants()).filter(k->k.toString().equals("DOG")).findFirst().orElseThrow();
        call(pet,"createEgg",new Class[]{String.class,kind},"New puppy",dog);call(pet,"save");
        Object restored=create(new Preferences());call(restored,"loadCloud",new Class[]{Map.class},cloud(pet));
        check(life(restored).created&&!life(restored).hatched&&(Boolean)call(restored,"hasCreatedPet"),"Egg isn't eligible for backup/restore");
        check(call(restored,"getName").equals("New puppy"),"Egg identity lost");
        check(((List<?>)call(restored,"memories")).size()==1,"Replaced pet wasn't archived");
        PetLife dying=life(restored);dying.hatched=true;dying.generation=2;dying.adultAgeMillis=7*86400000L;
        dying.advance(1,System.currentTimeMillis());call(restored,"updateFromClock");
        check(!dying.dead&&dying.deathReady(),"Old-age readiness was applied without consent");
        call(restored,"confirmDeath");call(restored,"save");
        List<?> memories=(List<?>)call(restored,"memories");check(memories.size()==2,"Old-age memorial not saved");
        JSONArray archives=new JSONArray((String)cloud(restored).get("historyJson"));
        check(archives.getJSONObject(1).getJSONObject("snapshot").getBoolean("dead"),"Full deceased progress not archived");
        Object reboot=create(new Preferences());call(reboot,"loadCloud",new Class[]{Map.class},cloud(restored));call(reboot,"updateFromClock");
        check(life(reboot).dead&&((List<?>)call(reboot,"memories")).size()==2,"Memorial isn't restored or is duplicated");
        call(reboot,"loadCloud",new Class[]{Map.class},oldCloud(now));
        check(((List<?>)call(reboot,"memories")).size()==2,"Older backup deletes saved memorials");
        call(reboot,"mergeHistory",new Class[]{String.class},"invalid history");
        check(((List<?>)call(reboot,"memories")).size()==2,"Bad remote history deletes memories");
        call(reboot,"updateFromClock"); // Finish offline catch-up before testing a new care tap.
        Class<?> action=Class.forName("com.example.shortsgesturecontrol.PetGameView$Action");
        int[] target={0,1,3,2};
        for(Object a:action.getEnumConstants()) {
            Arrays.fill(life(reboot).needs,50);float[] before=life(reboot).needs.clone();
            call(reboot,"apply",new Class[]{action},a);
            int changed=target[((Enum<?>)a).ordinal()];
            for(int i=0;i<4;i++)check(i==changed?life(reboot).needs[i]>before[i]:Math.abs(life(reboot).needs[i]-before[i])<.02,"Actual UI care action changes wrong need");
        }
        System.out.println("PASS: real Kotlin save model; legacy metrics/name/generation/cloud keys; restore-before-draw adult safety; egg backups; full archives; old/malformed history merge; actual care-action wiring.");
    }
}
