import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import javax.imageio.ImageIO;

/** Package generated separated artwork without replacing its antialiased alpha. */
public final class ExportPetLayers {
    static final class Region {
        int id,count,left=Integer.MAX_VALUE,top=Integer.MAX_VALUE,right,bottom;
        long sx,sy;
        double cx(){return (double)sx/count;} double cy(){return (double)sy/count;}
    }
    public static void main(String[] args) throws Exception {
        BufferedImage source=ImageIO.read(new File(args[0]));
        int w=source.getWidth(),h=source.getHeight();
        int[] labels=new int[w*h],queue=new int[w*h];
        ArrayList<Region> regions=new ArrayList<>();int next=0;
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
            int start=y*w+x;
            if(labels[start]!=0||(source.getRGB(x,y)>>>24)<32) continue;
            Region r=new Region();r.id=++next;int read=0,write=0;
            queue[write++]=start;labels[start]=r.id;
            while(read<write) {
                int at=queue[read++],xx=at%w,yy=at/w;
                r.count++;r.sx+=xx;r.sy+=yy;r.left=Math.min(r.left,xx);r.right=Math.max(r.right,xx);
                r.top=Math.min(r.top,yy);r.bottom=Math.max(r.bottom,yy);
                for(int dy=-1;dy<=1;dy++) for(int dx=-1;dx<=1;dx++) {
                    int nx=xx+dx,ny=yy+dy;
                    if(nx<0||nx>=w||ny<0||ny>=h) continue;
                    int index=ny*w+nx;
                    if(labels[index]==0&&(source.getRGB(nx,ny)>>>24)>=32) {
                        labels[index]=r.id;queue[write++]=index;
                    }
                }
            }
            if(r.count>100) regions.add(r);
        }
        regions.sort(Comparator.comparingInt((Region r)->r.count).reversed());
        if(regions.size()<9) throw new AssertionError("Expected nine separate parts, found "+regions.size());
        ArrayList<Region> parts=new ArrayList<>(regions.subList(0,9));
        parts.sort(Comparator.comparingDouble(Region::cy));
        for(int row=0;row<3;row++) parts.subList(row*3,row*3+3).sort(Comparator.comparingDouble(Region::cx));
        String extra=args[1].equals("dragon")?"wing":"ear";
        String[] names={"head","body","tail","front_near","front_far","rear_near","rear_far",extra+"_near",extra+"_far"};
        File output=new File(args[2]);if(!output.exists()&&!output.mkdirs()) throw new Exception("Cannot create output");
        for(int i=0;i<9;i++) {
            Region r=parts.get(i);
            int left=Math.max(0,r.left-3),top=Math.max(0,r.top-3);
            int width=Math.min(w-1,r.right+3)-left+1,height=Math.min(h-1,r.bottom+3)-top+1;
            BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<height;y++) for(int x=0;x<width;x++) {
                int xx=left+x,yy=top+y,pixel=source.getRGB(xx,yy),alpha=pixel>>>24;
                boolean keep=labels[yy*w+xx]==r.id;
                if(!keep&&alpha>0&&alpha<32) {
                    for(int dy=-3;dy<=3&&!keep;dy++) for(int dx=-3;dx<=3;dx++) {
                        int nx=xx+dx,ny=yy+dy;
                        if(nx>=0&&nx<w&&ny>=0&&ny<h&&labels[ny*w+nx]==r.id) {keep=true;break;}
                    }
                }
                image.setRGB(x,y,keep?pixel:0);
            }
            ImageIO.write(image,"png",new File(output,args[1]+"_rig_"+names[i]+".png"));
            System.out.printf("%s %s: %dx%d, source %d,%d, %d foreground pixels%n",args[1],names[i],width,height,left,top,r.count);
        }
    }
}
