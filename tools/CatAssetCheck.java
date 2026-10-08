import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Catch the binary-alpha export that produced opaque frayed/color-speckled edges. */
public class CatAssetCheck {
    public static void main(String[] args) throws Exception {
        for(String part:new String[]{"body","tail","front_near","front_far","rear_near","rear_far"}) {
            BufferedImage image=ImageIO.read(new File(args[0],"cat_rig_"+part+".png"));
            int fractional=0,speckles=0;
            for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
                int p=image.getRGB(x,y),a=p>>>24,r=(p>>16)&255,g=(p>>8)&255,b=p&255;
                if(a>0&&a<255) fractional++;
                boolean stray=(r>180&&g<70&&b<100)||(b>180&&r<90&&g<90)||(g>180&&r<90&&b<90);
                if(a>=128&&stray) speckles++;
            }
            if(fractional<50) throw new AssertionError(part+" lost soft-edge alpha");
            if(speckles>0) throw new AssertionError(part+" has "+speckles+" opaque colored edge speckles");
            System.out.printf("PASS: %s, %d fractional-alpha pixels, no opaque fringe speckles%n",part,fractional);
        }
    }
}
