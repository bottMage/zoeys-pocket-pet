package com.example.shortsgesturecontrol;

/** Complete whole-cel bounds, including antialiasing and either facing direction. */
public final class PetSpriteLayout {
    private PetSpriteLayout() {}

    public static int frameCount(String kind) {return kind.equals("dragon")?11:12;}

    public static int assetFrame(String kind,int slot) {
        int index=Math.floorMod(slot,frameCount(kind));
        // The archived dragon cel 5 contains a duplicate tail cut off at the
        // source edge. Omit that cel instead of repainting the design or
        // holding an adjacent image twice; all remaining artwork is original.
        return kind.equals("dragon")&&index>=5?index+1:index;
    }

    public static final class Bounds {
        public final int left,top,right,bottom,width,height;
        public Bounds(int left,int top,int right,int bottom,int width,int height) {
            if(width<=0||height<=0||left<0||top<0||right>width||bottom>height||right<=left||bottom<=top)
                throw new IllegalArgumentException("Invalid pet alpha bounds");
            this.left=left;this.top=top;this.right=right;this.bottom=bottom;this.width=width;this.height=height;
        }
    }

    public static final class Envelope {
        public double reach,height;
        public void include(Bounds frame) {
            reach=Math.max(reach,Math.max(frame.width*.5-frame.left,frame.right-frame.width*.5)/frame.width);
            height=Math.max(height,(double)(frame.bottom-frame.top)/frame.width);
        }
    }

    public static Bounds scan(int[] pixels,int width,int height) {
        if(width<=0||height<=0||pixels.length!=width*height)throw new IllegalArgumentException("Invalid sprite pixels");
        int left=width,top=height,right=-1,bottom=-1;
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)if((pixels[y*width+x]>>>24)!=0) {
            left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x);bottom=Math.max(bottom,y);
        }
        if(right<0)throw new IllegalArgumentException("Empty sprite");
        return new Bounds(left,top,right+1,bottom+1,width,height);
    }

    public static final class Layout {
        public final double artWidth,minCenter,maxCenter;
        Layout(double artWidth,double minCenter,double maxCenter) {
            this.artWidth=artWidth;this.minCenter=minCenter;this.maxCenter=maxCenter;
        }
    }

    public static Layout fit(Envelope envelope,double requestedWidth,double sceneLeft,double sceneRight,
                             double ceiling,double ground,double margin,double travelLane) {
        if(envelope.reach<=0||envelope.height<=0)throw new IllegalArgumentException("Empty pet envelope");
        double available=Math.max(0,sceneRight-sceneLeft-2*margin);
        double lane=Math.min(Math.max(0,travelLane),available*.4);
        double width=Math.max(0,Math.min(requestedWidth,Math.min((available-lane)/(2*envelope.reach),
            Math.max(0,ground-ceiling)/envelope.height)));
        double reach=width*envelope.reach;
        return new Layout(width,sceneLeft+margin+reach,sceneRight-margin-reach);
    }
}
