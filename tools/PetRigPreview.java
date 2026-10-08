import com.example.shortsgesturecontrol.PetRig;
import com.example.shortsgesturecontrol.PetMotion;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import javax.imageio.ImageIO;

/** Render production species meshes/poses; never used as runtime animation cels. */
public final class PetRigPreview {
    static File assets;
    static final HashMap<String,BufferedImage> cache=new HashMap<>();
    static BufferedImage load(PetRig rig,PetRig.Part part) throws Exception {
        String key=rig.kind+"_rig_"+part.name;
        if(!cache.containsKey(key)) {
            BufferedImage source=ImageIO.read(new File(assets,key+".png"));
            BufferedImage image=new BufferedImage(source.getWidth(),source.getHeight(),BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
                int p=source.getRGB(x,y),mask=rig.attachmentAlpha(part,(x+.5)/image.getWidth(),(y+.5)/image.getHeight());
                image.setRGB(x,y,(p&0xffffff)|((p>>>24)*mask/255<<24));
            }
            cache.put(key,image);
        }
        return cache.get(key);
    }
    static void drawPart(Graphics2D g,PetRig rig,PetRig.Part part,PetMotion motion) throws Exception {
        BufferedImage image=load(rig,part);
        if(part instanceof PetRig.Leg) {
            PetRig.Leg leg=(PetRig.Leg)part;PetMotion.Foot foot=motion.feet[leg.index];
            rig.skin(leg,motion.pose,foot.x,foot.lift);
            int cols=PetRig.COLS,rows=PetRig.ROWS;
            double[] sx=new double[(cols+1)*(rows+1)],sy=new double[sx.length];
            for(int r=0;r<=rows;r++) for(int c=0;c<=cols;c++) {
                int index=r*(cols+1)+c;sx[index]=(double)image.getWidth()*c/cols;sy[index]=(double)image.getHeight()*r/rows;
            }
            for(int r=0;r<rows;r++) for(int c=0;c<cols;c++) {
                int a=r*(cols+1)+c,b=a+1,d=a+cols+1,e=d+1;
                CatRigPreview.triangle(g,image,sx,sy,leg.vertices,a,b,d);
                CatRigPreview.triangle(g,image,sx,sy,leg.vertices,b,e,d);
            }
        } else {
            Graphics2D t=(Graphics2D)g.create();
            t.translate(0,motion.pose.y);t.rotate(motion.pose.angle,rig.cx,rig.cy);
            if(part.headParent)t.rotate(motion.pose.headAngle,rig.head.px,rig.head.py);
            t.rotate(motion.pose.angle(part),part.px,part.py);
            AffineTransform transform=AffineTransform.getTranslateInstance(part.x,part.y);
            if(part.flipX){transform.translate(part.width,0);transform.scale(-part.width/image.getWidth(),part.height/image.getHeight());}
            else transform.scale(part.width/image.getWidth(),part.height/image.getHeight());
            t.drawImage(image,transform,null);t.dispose();
        }
    }
    static void panel(Graphics2D g,PetRig rig,PetMotion motion) throws Exception {
        g.setColor(new Color(233,234,247));g.fillRect(0,0,800,540);
        g.setColor(new Color(185,224,194));g.fillRect(0,488,800,52);
        g.setColor(new Color(150,193,161));for(int x=0;x<800;x+=40)g.fillRect(x,490,2,9);
        double root=32+motion.position*224;
        g.setColor(new Color(147,184,154));g.fill(new Ellipse2D.Double(root+108,480,240,16));
        g.setColor(new Color(73,57,93));g.setFont(new Font("SansSerif",Font.BOLD,19));g.drawString(rig.kind.toUpperCase(),22,31);
        g.translate(root,20);if(motion.facingRight){g.translate(512,0);g.scale(-1,1);}
        for(PetRig.Part part:rig.parts)drawPart(g,rig,part,motion);
    }
    public static void main(String[] args) throws Exception {
        assets=new File(args[0]);File output=new File(args[1]);output.mkdirs();
        String kind=args[2];int frames=args.length>3?Integer.parseInt(args[3]):540;
        String[] kinds=kind.equals("all")?new String[]{"dog","bunny","hamster","dragon"}:new String[]{kind};
        PetRig[] rigs=new PetRig[kinds.length];PetMotion[] motions=new PetMotion[kinds.length];
        for(int i=0;i<kinds.length;i++){rigs[i]=new PetRig(kinds[i]);motions[i]=new PetMotion(rigs[i]);}
        for(int frame=0;frame<frames;frame++) {
            BufferedImage image=new BufferedImage(kinds.length==4?1600:800,kinds.length==4?1080:540,BufferedImage.TYPE_INT_RGB);
            for(int i=0;i<kinds.length;i++) {
                motions[i].advance(1.0/60,224,1,false);
                Graphics2D g=image.createGraphics();g.translate((i%2)*800,(i/2)*540);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_OFF);
                panel(g,rigs[i],motions[i]);g.dispose();
            }
            ImageIO.write(image,"png",new File(output,String.format("frame-%03d.png",frame)));
        }
    }
}
