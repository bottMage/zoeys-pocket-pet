import com.example.shortsgesturecontrol.PetGrowth;
import com.example.shortsgesturecontrol.PetSpriteLayout;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Offline stage review using production sprite bounds, size and coat matrices. */
public final class GrowthStagePreview {
    static final String[] KINDS={"cat","dog","bunny","hamster","dragon"};
    static final String[] LABELS={"EGG","BABY","YOUNG","ADULT"};
    static final Color INK=new Color(68,43,90);
    static BufferedImage tint(BufferedImage source,float[] m) {
        int w=source.getWidth(),h=source.getHeight();BufferedImage result=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
            int p=source.getRGB(x,y),r=(p>>>16)&255,g=(p>>>8)&255,b=p&255;
            int[] values=new int[3];
            for(int row=0;row<3;row++)values[row]=Math.max(0,Math.min(255,Math.round(m[row*5]*r+m[row*5+1]*g+m[row*5+2]*b+m[row*5+4])));
            result.setRGB(x,y,(p&0xff000000)|(values[0]<<16)|(values[1]<<8)|values[2]);
        }
        return result;
    }
    static void egg(Graphics2D g,int kind) {
        boolean dragon=kind==4;
        int[][] shells={{221,199,240},{255,220,163},{202,238,211},{255,227,174},{171,233,234}};
        int[][] spots={{173,135,207},{217,156,94},{141,195,158},{226,172,91},{86,177,185}};
        g.setColor(dragon?new Color(137,92,57):new Color(222,182,101));
        g.fill(new Ellipse2D.Double(-94,-19,188,34));g.setStroke(new BasicStroke(3));
        for(int s=0;s<=25;s++) {
            float x=-91+s*7;Path2D p=new Path2D.Float();
            p.moveTo(x-8,-6+(s%4)*3);p.quadTo(x+12,-27+(s%5)*4,x+29,-6+(s%3)*3);
            g.setColor(dragon?(s%2==0?new Color(173,123,72):new Color(107,72,48)):(s%2==0?new Color(250,217,144):new Color(195,151,76)));g.draw(p);
        }
        Color shell=new Color(shells[kind][0],shells[kind][1],shells[kind][2]);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(-20,-110),142,new float[]{0,1},new Color[]{new Color(255,248,225),shell}));
        g.fill(new Ellipse2D.Double(-48,-146,96,148));
        g.setColor(new Color(spots[kind][0],spots[kind][1],spots[kind][2]));
        g.fill(new Ellipse2D.Double(5,-127,16,16));g.fill(new Ellipse2D.Double(-34,-83,19,21));g.fill(new Ellipse2D.Double(15,-52,19,19));
        g.setColor(new Color(255,255,255,110));g.fill(new Ellipse2D.Double(-31,-119,14,37));
        g.setStroke(new BasicStroke(dragon?3.5f:2));
        for(int s=0;s<=20;s++) {
            float x=-91+s*8;Path2D p=new Path2D.Float();
            p.moveTo(x-9,2+(s%3)*2);p.quadTo(x+7,16+s%4,x+24,-1+(s%3)*2);
            g.setColor(dragon?(s%2==0?new Color(178,128,76):new Color(114,79,50)):(s%2==0?new Color(247,213,127):new Color(208,164,88)));g.draw(p);
        }
    }
    static void centered(Graphics2D g,String text,int center,int y,Font font) {
        g.setFont(font);g.setColor(INK);g.drawString(text,center-g.getFontMetrics().stringWidth(text)/2,y);
    }
    public static void main(String[] args)throws Exception {
        File assets=new File(args[0]),out=new File(args[1]);out.mkdirs();
        BufferedImage overview=new BufferedImage(1320,1680,BufferedImage.TYPE_INT_RGB);
        Graphics2D all=overview.createGraphics();all.setColor(new Color(250,247,252));all.fillRect(0,0,1320,1680);
        for(int k=0;k<KINDS.length;k++) {
            String kind=KINDS[k];PetSpriteLayout.Envelope envelope=new PetSpriteLayout.Envelope();
            BufferedImage original=ImageIO.read(new File(assets,"walk_"+kind+"_0.png"));PetSpriteLayout.Bounds first=null;
            for(int slot=0;slot<PetSpriteLayout.frameCount(kind);slot++) {
                int index=PetSpriteLayout.assetFrame(kind,slot);
                BufferedImage image=ImageIO.read(new File(assets,"walk_"+kind+"_"+index+".png"));int w=image.getWidth(),h=image.getHeight();
                PetSpriteLayout.Bounds bounds=PetSpriteLayout.scan(image.getRGB(0,0,w,h,null,0,w),w,h);envelope.include(bounds);
                if(slot==0)first=bounds;
            }
            PetSpriteLayout.Layout layout=PetSpriteLayout.fit(envelope,296,18,394,134,478,6,68);
            BufferedImage row=new BufferedImage(1320,336,BufferedImage.TYPE_INT_RGB);
            Graphics2D rowG=row.createGraphics();
            for(int stage=0;stage<4;stage++) {
                Graphics2D g=(Graphics2D)rowG.create();g.translate(stage*330,0);
                g.setColor(new Color(233,234,247));g.fillRect(0,0,330,336);
                g.setColor(new Color(184,225,194));g.fillRect(0,272,330,64);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                centered(g,LABELS[stage],165,31,new Font("SansSerif",Font.BOLD,18));
                centered(g,kind.toUpperCase(),165,57,new Font("SansSerif",Font.PLAIN,14));
                if(stage==0) {
                    g.translate(165,272);g.scale(.8,.8);egg(g,k);
                } else {
                    PetGrowth.Stage growth=PetGrowth.Stage.values()[stage];
                    double width=layout.artWidth*growth.size*.8,scale=width/original.getWidth();
                    g.setColor(new Color(67,57,82,58));g.fill(new Ellipse2D.Double(165-width*.25,269,width*.5,8));
                    AffineTransform transform=AffineTransform.getTranslateInstance(165-width*.5,272-first.bottom*scale);
                    transform.scale(scale,scale);g.drawImage(tint(original,PetGrowth.colorMatrix(kind,growth)),transform,null);
                }
                g.dispose();
                Graphics2D caption=(Graphics2D)rowG.create();caption.translate(stage*330,0);
                centered(caption,stage==0?"About 2 days + care":"About 1 week + care",165,315,new Font("SansSerif",Font.PLAIN,14));caption.dispose();
            }
            rowG.dispose();all.drawImage(row,0,k*336,null);ImageIO.write(row,"png",new File(out,kind+"-stages.png"));
        }
        all.dispose();ImageIO.write(overview,"png",new File(out,"all-stages.png"));
    }
}
