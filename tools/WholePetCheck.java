import com.example.shortsgesturecontrol.PetSpriteLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Uses actual cel alpha and the production layout, not guessed tail bounds. */
public final class WholePetCheck {
    public static void main(String[] args) throws Exception {
        String[] kinds={"cat","dog","bunny","hamster","dragon"};
        int cases=0;
        for(String kind:kinds) {
            int frameCount=PetSpriteLayout.frameCount(kind);
            PetSpriteLayout.Bounds[] bounds=new PetSpriteLayout.Bounds[frameCount];
            PetSpriteLayout.Envelope envelope=new PetSpriteLayout.Envelope();
            for(int frame=0;frame<frameCount;frame++) {
                int asset=PetSpriteLayout.assetFrame(kind,frame);
                if(kind.equals("dragon")&&asset==5)throw new AssertionError("Malformed tail cel selected");
                BufferedImage image=ImageIO.read(new File(args[0],"walk_"+kind+"_"+asset+".png"));
                if(image==null)throw new AssertionError("Missing whole-pet cel");
                int w=image.getWidth(),h=image.getHeight();
                bounds[frame]=PetSpriteLayout.scan(image.getRGB(0,0,w,h,null,0,w),w,h);
                if(bounds[frame].left==0||bounds[frame].top==0||bounds[frame].right==w||bounds[frame].bottom==h)
                    throw new AssertionError("Source silhouette touches cel border: "+kind+" "+frame);
                envelope.include(bounds[frame]);
            }
            for(double width:new double[]{240,280,320,360,412,480,600})
            for(double height:new double[]{560,600,640,720,800,900})
            for(double stage:new double[]{.90,.96,1,1.08}) {
                double ground=height-382,ceiling=125,margin=6;
                PetSpriteLayout.Layout layout=PetSpriteLayout.fit(envelope,Math.min(width-42,296)*stage,
                    18,width-18,ceiling+9,ground,margin,68);
                if(layout.artWidth<=0||layout.minCenter>layout.maxCenter)
                    throw new AssertionError("Invalid scene layout");
                for(PetSpriteLayout.Bounds b:bounds)
                for(boolean mirrored:new boolean[]{false,true})
                for(double position:new double[]{0,.25,.5,.75,1})
                for(double bounce:new double[]{0,-9}) {
                    double scale=layout.artWidth/b.width;
                    double center=layout.minCenter+position*(layout.maxCenter-layout.minCenter);
                    double left=center-layout.artWidth*.5+scale*(mirrored?b.width-b.right:b.left);
                    double right=center-layout.artWidth*.5+scale*(mirrored?b.width-b.left:b.right);
                    double top=ground+bounce-scale*(b.bottom-b.top);
                    if(left<18+margin-1e-6||right>width-18-margin+1e-6||top<ceiling-1e-6)
                        throw new AssertionError("Scenery clips silhouette: "+kind);
                    cases++;
                }
            }
            System.out.printf("PASS %s: %d padded whole-pet cels; envelope reach %.4f, height %.4f%n",kind,frameCount,envelope.reach,envelope.height);
        }
        // Exclusive right/bottom edges include the entire final alpha pixel.
        PetSpriteLayout.Bounds one=PetSpriteLayout.scan(new int[]{0,0,0,0,0,1<<24},3,2);
        if(one.left!=2||one.top!=1||one.right!=3||one.bottom!=2)throw new AssertionError("Alpha scan off by one");
        System.out.println("PASS: "+cases+" bounds checks, both facings, all stages and scene ends, including play bounce.");
    }
}
