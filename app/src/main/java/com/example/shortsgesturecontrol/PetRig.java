package com.example.shortsgesturecontrol;

/** Species-specific authoring geometry; the existing cat rig is left intact. */
public final class PetRig {
    public static final int COLS=8,ROWS=20;
    public static final double SIZE=512,GROUND=468;
    public final String kind;
    public final double speed,stride,stance,lift,crouch,weightShift,pitch,headArc,tailArc,earArc,wingArc,cx,cy;
    public final Leg[] legs;
    public final Part head,body,tail,nearExtra,farExtra;
    /** Shared depth order for Android and the offline preview. */
    public final Part[] parts;

    public static class Part {
        public final String name;
        public final double x,y,width,height,px,py;
        public final boolean headParent;
        public final int channel;
        public final double gain;
        public final boolean flipX;
        Part(String name,double x,double y,double width,double height,double px,double py,boolean headParent,int channel,double gain) {
            this(name,x,y,width,height,px,py,headParent,channel,gain,false);
        }
        Part(String name,double x,double y,double width,double height,double px,double py,boolean headParent,int channel,double gain,boolean flipX) {
            this.name=name;this.x=x;this.y=y;this.width=width;this.height=height;
            this.px=px;this.py=py;this.headParent=headParent;this.channel=channel;this.gain=gain;
            this.flipX=flipX;
        }
    }
    public static final class Leg extends Part {
        public final double hx,hy,kx,ky,fx,fy,restX,phase,upper,lower,length,bend;
        public final int index;
        public final float[] vertices=new float[(COLS+1)*(ROWS+1)*2];
        Leg(String name,int index,double hipX,double height,double width,double hipFraction,double kneeFraction,
            double footFraction,double phase,double restOffset,double pad) {
            this(name,index,hipX,height,width,hipFraction,kneeFraction,footFraction,phase,restOffset,pad,.13);
        }
        Leg(String name,int index,double hipX,double height,double width,double hipFraction,double kneeFraction,
            double footFraction,double phase,double restOffset,double pad,double hipHeight) {
            super(name,hipX-width*hipFraction,GROUND-height,width,height,0,0,false,0,0);
            this.index=index;hx=hipX;hy=y+height*hipHeight;kx=x+width*kneeFraction;ky=y+height*.57;
            fx=x+width*footFraction;fy=GROUND-pad;restX=hipX+restOffset;this.phase=phase;
            upper=Math.hypot(kx-hx,ky-hy);lower=Math.hypot(fx-kx,fy-ky);length=upper+lower;
            double sign=Math.signum((fx-hx)*(ky-hy)-(fy-hy)*(kx-hx));bend=sign==0?-1:sign;
        }
    }
    // Rigid channels: torso=0, head=1, tail=2, ears=3, wings=4.
    private static Part part(String name,double x,double y,double w,double h,double px,double py,int channel) {
        return new Part(name,x,y,w,h,px,py,false,channel,1);
    }
    private static Part ear(String name,double x,double y,double w,double h,double px,double py,double gain) {
        return new Part(name,x,y,w,h,px,py,true,3,gain);
    }
    public PetRig(String kind) {
        this.kind=kind;
        switch(kind) {
            case "dog":
                speed=82;stride=64;stance=.64;lift=26;crouch=5;weightShift=1.7;pitch=.025;
                headArc=.029;tailArc=.065;earArc=.075;wingArc=0;cx=285;cy=350;
                head=part("head",65,100,200,278,195,350,1);
                body=part("body",182,285,218,165,285,350,0);
                tail=part("tail",365,230,128,152,377,335,2);
                nearExtra=ear("ear_near",223,148,118,111,235,169,1);
                farExtra=new Part("ear_far",50,165,80,79,119,182,true,3,.7,true);
                legs=new Leg[]{
                    new Leg("rear_far",0,289,145,88,.60,.57,.32,.95,8,14,.25),
                    new Leg("front_far",1,225,148,104,.64,.54,.39,.5,-2,14),
                    new Leg("rear_near",2,382,152,123,.45,.63,.73,.45,8,14,.25),
                    new Leg("front_near",3,195,152,135,.74,.54,.26,0,-2,14)};
                break;
            case "bunny":
                speed=78;stride=54;stance=.60;lift=24;crouch=6;weightShift=2.5;pitch=.033;
                headArc=.032;tailArc=.03;earArc=.075;wingArc=0;cx=285;cy=375;
                head=part("head",70,210,175,220,199,380,1);
                body=part("body",192,325,205,128,285,375,0);
                tail=part("tail",377,327,63,64,388,359,2);
                nearExtra=ear("ear_near",160,90,132,171,179,243,1);
                farExtra=ear("ear_far",115,103,115,145,132,234,.8);
                legs=new Leg[]{
                    new Leg("rear_far",0,338,142,108,.45,.65,.59,.51,8,12,.30),
                    new Leg("front_far",1,227,105,90,.42,.62,.26,.06,-2,12),
                    new Leg("rear_near",2,394,150,146,.45,.64,.63,.45,7,12,.30),
                    new Leg("front_near",3,201,110,86,.45,.70,.28,0,-2,12)};
                break;
            case "hamster":
                speed=66;stride=32;stance=.65;lift=10;crouch=2.5;weightShift=.7;pitch=.018;
                headArc=.025;tailArc=.014;earArc=.022;wingArc=0;cx=310;cy=400;
                head=part("head",43,192,266,257,230,402,1);
                body=part("body",250,271,198,184,310,400,0);
                tail=part("tail",429,366,24,25,434,379,2);
                nearExtra=ear("ear_near",211,174,65,67,225,229,1);
                farExtra=ear("ear_far",92,176,52,58,123,224,.7);
                legs=new Leg[]{
                    new Leg("rear_far",0,353,73,61,.40,.62,.45,.75,5,8),
                    new Leg("front_far",1,225,70,49,.62,.61,.29,.5,-2,8),
                    new Leg("rear_near",2,405,82,62,.44,.67,.39,.25,5,8),
                    new Leg("front_near",3,199,75,74,.49,.73,.25,0,-2,8)};
                break;
            case "dragon":
                speed=70;stride=58;stance=.66;lift=21;crouch=5;weightShift=1.3;pitch=.020;
                headArc=.027;tailArc=.038;earArc=0;wingArc=.046;cx=290;cy=350;
                head=part("head",85,120,205,242,190,340,1);
                body=part("body",190,283,225,161,290,350,0);
                tail=part("tail",345,283,148,126,355,375,2);
                nearExtra=part("wing_near",247,176,184,167,260,307,4);
                farExtra=new Part("wing_far",237,187,174,141,247,306,false,4,.72);
                legs=new Leg[]{
                    new Leg("rear_far",0,354,135,127,.45,.61,.63,.75,10,13,.30),
                    new Leg("front_far",1,225,140,118,.72,.61,.27,.5,-2,13),
                    new Leg("rear_near",2,391,140,140,.45,.62,.74,.25,10,13,.30),
                    new Leg("front_near",3,198,145,129,.72,.60,.26,0,-2,13)};
                break;
            default:throw new IllegalArgumentException("No non-cat rig for "+kind);
        }
        if(kind.equals("dragon")) parts=new Part[]{farExtra,legs[0],legs[1],body,tail,legs[2],legs[3],nearExtra,head};
        else parts=new Part[]{farExtra,legs[0],legs[1],body,tail,legs[2],legs[3],head,nearExtra};
    }

    public static final class Pose {
        public double y,angle,headAngle,tailAngle,earAngle,wingAngle;
        private double ca=1,sa=0,cx,cy;
        public Pose(PetRig rig) {cx=rig.cx;cy=rig.cy;}
        public void sample(PetRig rig,double cycle,double activity,double seconds) {
            double p=2*Math.PI*cycle;
            // Rabbit compression follows the paired hind-leg push, not four
            // independent cat shoulders. The trunk remains one rigid entity.
            double pulse=rig.kind.equals("bunny")?1-Math.cos(p):1-Math.cos(2*p);
            y=activity*(rig.crouch+rig.weightShift*pulse);
            angle=activity*rig.pitch*Math.sin(p-.35);ca=Math.cos(angle);sa=Math.sin(angle);
            headAngle=-angle*.65+activity*rig.headArc*Math.sin(p-.25)+(1-activity)*.012*Math.sin(seconds*.75);
            tailAngle=-angle*.5+activity*rig.tailArc*Math.sin(p-.8)+(1-activity)*rig.tailArc*.28*Math.sin(seconds*.9);
            earAngle=-headAngle*.45+activity*rig.earArc*Math.sin(p-1.1);
            wingAngle=-angle*.55+activity*rig.wingArc*Math.sin(p-.9)+(1-activity)*.012*Math.sin(seconds*.8);
        }
        public double x(double x,double y){return cx+(x-cx)*ca-(y-cy)*sa;}
        public double y(double x,double y){return cy+this.y+(x-cx)*sa+(y-cy)*ca;}
        public double angle(Part part) {
            switch(part.channel){case 1:return headAngle;case 2:return tailAngle;case 3:return earAngle*part.gain;case 4:return wingAngle*part.gain;default:return 0;}
        }
    }
    public int attachmentAlpha(Part part,double u,double v) {
        if(part instanceof Leg && part.name.endsWith("near")) {
            Leg l=(Leg)part;double fade=Math.min(24,l.height*.19);
            double alpha=CatRig.ease((l.y+v*l.height-(l.hy-4))/fade);
            if(part.name.startsWith("rear") && !kind.equals("hamster")) {
                // The generated rear endpoint is on the left of the haunch,
                // not at the internal hip pivot. Open that cosmetic oval only.
                alpha*=CatRig.ease((u-.23)/.12)+ (1-CatRig.ease((u-.23)/.12))*CatRig.ease((v-.38)/.12);
            }
            return (int)Math.round(255*alpha);
        }
        if(part==tail || part.channel==4 || part.channel==3) {
            // Hide authoring end caps, not the free contour. Root is inherited
            // from the appropriate body/head parent, so there is no joint drift.
            double radius=part.channel==3?Math.min(18,part.width*.18):Math.min(22,part.width*.19);
            double dx=part.x+(part.flipX?1-u:u)*part.width-part.px,dy=part.y+v*part.height-part.py;
            return (int)Math.round(255*CatRig.ease((Math.hypot(dx,dy)-radius*.75)/(radius*.85)));
        }
        return 255;
    }
    public void skin(Leg l,Pose pose,double footX,double lift) {
        double fx=l.restX+footX,fy=l.fy-lift,hx=pose.x(l.hx,l.hy),hy=pose.y(l.hx,l.hy);
        double dx=fx-hx,dy=fy-hy,dist=Math.max(.0001,Math.hypot(dx,dy));
        double d=Math.max(Math.abs(l.upper-l.lower)+.001,Math.min(dist,l.length-.001));
        double along=(l.upper*l.upper-l.lower*l.lower+d*d)/(2*d);
        double across=Math.sqrt(Math.max(0,l.upper*l.upper-along*along))*l.bend;
        double ux=dx/dist,uy=dy/dist,kx=hx+ux*along-uy*across,ky=hy+uy*along+ux*across;
        double a=Math.atan2(ky-hy,kx-hx)-Math.atan2(l.ky-l.hy,l.kx-l.hx);
        double b=Math.atan2(fy-ky,fx-kx)-Math.atan2(l.fy-l.ky,l.fx-l.kx);
        double ca=Math.cos(a),sa=Math.sin(a),cb=Math.cos(b),sb=Math.sin(b);
        double jointBlend=Math.min(24,l.height*.20),pawBlend=Math.min(18,l.height*.16),rootBlend=Math.min(25,l.height*.20);
        int index=0;
        for(int row=0;row<=ROWS;row++) {
            double y=l.y+l.height*row/ROWS;
            double lower=smooth((y-l.ky+jointBlend*.5)/jointBlend);
            double paw=smooth((y-l.fy+pawBlend-2)/pawBlend);
            double root=1-smooth((y-l.hy+2)/rootBlend);
            for(int col=0;col<=COLS;col++) {
                double x=l.x+l.width*col/COLS;
                double ax=hx+(x-l.hx)*ca-(y-l.hy)*sa,ay=hy+(x-l.hx)*sa+(y-l.hy)*ca;
                double bx=kx+(x-l.kx)*cb-(y-l.ky)*sb,by=ky+(x-l.kx)*sb+(y-l.ky)*cb;
                double vx=ax+(bx-ax)*lower,vy=ay+(by-ay)*lower;
                vx+=(pose.x(x,y)-vx)*root;vy+=(pose.y(x,y)-vy)*root;
                vx+=(fx+x-l.fx-vx)*paw;vy+=(fy+y-l.fy-vy)*paw;
                l.vertices[index++]=(float)vx;l.vertices[index++]=(float)vy;
            }
        }
    }
    static double smooth(double t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    public double footLift(double t){t=Math.max(0,Math.min(1,t));double arch=t*(1-t);return lift*64*arch*arch*arch;}
}
