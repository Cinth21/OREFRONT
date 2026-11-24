package com.student.orefront;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.*;
import java.util.concurrent.*;

/** OREFRONT Enhanced: native portrait tactical RTS, entirely on-device, with real phone-hosted LAN. */
public final class MainActivity extends Activity implements LanRoom.Listener {
    private enum Page { HOME, LOBBY, BATTLE }
    private Page page=Page.HOME;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService offlineClock=Executors.newSingleThreadScheduledExecutor();
    private GameView view;
    private volatile GameCore state;
    private LanRoom.Server server;
    private LanRoom.Client client;
    private boolean offline=false,connected=false;
    private String room="",hostIP="",info="",intel="",username="Commander";
    private String token=UUID.randomUUID().toString();
    private int myId=0,selectedPlayer=0,selectedUnit=-1,selectedBuilding=-1,tab=0;
    private String placement="";

    @Override public void onCreate(Bundle b){super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(5894|1024|512);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        username=getPreferences(0).getString("name","Commander");
        token=getPreferences(0).getString("token",UUID.randomUUID().toString());
        getPreferences(0).edit().putString("token",token).apply();
        view=new GameView(this);setContentView(view);
        offlineClock.scheduleAtFixedRate(()->{
            if(!offline||state==null||!state.phase.equals("PLAY"))return;
            ui.post(()->{if(offline&&state!=null&&state.phase.equals("PLAY")){state.tick();view.invalidate();}});
        },500,500,TimeUnit.MILLISECONDS);
    }
    @Override protected void onDestroy(){leaveRoom();offlineClock.shutdownNow();super.onDestroy();}
    @Override public void onBackPressed(){if(page==Page.HOME)super.onBackPressed();else new AlertDialog.Builder(this)
        .setTitle("Leave OREFRONT?").setMessage("The host's match will end if the host leaves.")
        .setNegativeButton("Cancel",null).setPositiveButton("Leave",(d,w)->leaveRoom()).show();}
    private void toast(String text){ui.post(()->{info=text;Toast.makeText(this,text,Toast.LENGTH_SHORT).show();view.invalidate();});}
    private void refresh(GameCore w,int id){ui.post(()->{state=w;myId=id;connected=true;
        page=w.phase.equals("LOBBY")?Page.LOBBY:Page.BATTLE;view.invalidate();});}
    @Override public void onState(GameCore w,int id){refresh(w,id);}
    @Override public void onMessage(String msg){ui.post(()->{if(msg.startsWith("INTEL"))intel=msg;else info=msg;view.invalidate();});}
    @Override public void onDisconnected(String msg){ui.post(()->{connected=false;info=msg;view.invalidate();
        new AlertDialog.Builder(this).setTitle("Connection lost").setMessage(msg+"\nYour faction remains at the host. Use Join Room with the same phone to reconnect.")
        .setPositiveButton("OK",null).show();});}
    private void leaveRoom(){
        if(client!=null){client.command("LEAVE");client.close();client=null;}
        if(server!=null){server.close();server=null;}
        offline=false;connected=false;page=Page.HOME;room="";hostIP="";state=null;
        placement="";selectedUnit=-1;selectedBuilding=-1;tab=0;info="";intel="";
        if(view!=null)view.invalidate();
    }
    private void chooseName(Runnable next){EditText input=new EditText(this);input.setSingleLine(true);input.setText(username);input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle("Commander username").setView(input).setNegativeButton("Cancel",null)
            .setPositiveButton("Continue",(d,w)->{username=GameCore.clean(input.getText().toString());
                getPreferences(0).edit().putString("name",username).apply();next.run();}).show();}
    private void startAI(){new AlertDialog.Builder(this).setTitle("Choose opponent intelligence")
        .setItems(new String[]{"EASY • 2 enemy factions","MEDIUM • 3 enemy factions","HARD • 5 enemy factions"},(d,which)->{
            leaveRoom();offline=true;connected=true;state=new GameCore();state.addOfflineAI(which==0?2:which==1?3:5,which);
            page=Page.BATTLE;myId=0;info="AI factions use economy, combat priorities and BFS routing.";view.invalidate();
        }).show();}
    private void host(){leaveRoom();new Thread(()->{try{
        LanRoom.Server s=new LanRoom.Server(username,token,this);
        ui.post(()->{server=s;room=s.code();hostIP=s.ip();connected=true;page=Page.LOBBY;
            info="Local room hosted on this Android phone";state=s.snapshot();view.invalidate();});
    }catch(Exception ex){toast("Host failed: "+ex.getMessage());}},"orefront-host-init").start();}
    private void manualJoin(){
        LinearLayout form=new LinearLayout(this);form.setPadding(32,18,32,6);form.setOrientation(LinearLayout.VERTICAL);
        TextView hint=new TextView(this);hint.setText("Use the same Wi-Fi or hotspot. Room code is required. Host IP is optional if discovery works.");form.addView(hint);
        EditText code=new EditText(this);code.setHint("6-digit room code");code.setInputType(InputType.TYPE_CLASS_NUMBER);form.addView(code);
        EditText ip=new EditText(this);ip.setHint("Host IP (optional)");ip.setSingleLine(true);form.addView(ip);
        new AlertDialog.Builder(this).setTitle("JOIN LOCAL LAN ROOM").setView(form).setNegativeButton("Cancel",null)
            .setPositiveButton("Join",(d,w)->{String c=code.getText().toString().trim(),addr=ip.getText().toString().trim();
                if(!c.matches("[0-9]{6}")){toast("Room code must contain six digits");return;}join(c,addr);}).show();
    }
    private void join(String c,String address){leaveRoom();info="Searching for the host phone...";view.invalidate();
        new Thread(()->{String ip=address;try{
            if(ip.isEmpty()){for(LanRoom.RoomInfo r:LanRoom.search(2300))if(r.code.equals(c)){ip=r.host;break;}
                if(ip.isEmpty())throw new IllegalStateException("Room not found. Enter the host IP displayed on its lobby.");}
            final String dest=ip;LanRoom.Client joined=new LanRoom.Client(dest,c,username,token,this);
            ui.post(()->{client=joined;room=c;hostIP=dest;connected=true;
                if(state==null||state.phase.equals("LOBBY"))page=Page.LOBBY;else page=Page.BATTLE;
                info="Connected to "+dest;view.invalidate();});
        }catch(Exception ex){toast("Join failed: "+ex.getMessage());}},"orefront-connect").start();}
    private void searchRooms(){info="Discovering rooms on Wi-Fi...";view.invalidate();new Thread(()->{
        List<LanRoom.RoomInfo> rooms=LanRoom.search(2400);
        ui.post(()->{if(rooms.isEmpty())new AlertDialog.Builder(this).setTitle("No rooms discovered")
            .setMessage("Broadcast discovery might be blocked by this hotspot. Use the room code + Host IP instead.")
            .setPositiveButton("Join manually",(d,w)->chooseName(this::manualJoin)).setNegativeButton("Close",null).show();
            else{String[] labels=new String[rooms.size()];for(int i=0;i<rooms.size();i++)labels[i]=rooms.get(i).toString();
                new AlertDialog.Builder(this).setTitle("LAN rooms nearby").setItems(labels,(d,n)->chooseName(()->join(rooms.get(n).code,rooms.get(n).host)))
                    .setNegativeButton("Cancel",null).show();}info="";view.invalidate();});
    },"orefront-discovery").start();}
    private boolean command(String order){
        if(offline){if(state==null)return false;String[] c=order.split("\\|");boolean ok=false;
            try{switch(c[0]){
                case "BUILD":ok=state.build(0,c[1],Integer.parseInt(c[2]),Integer.parseInt(c[3]));break;
                case "UPGRADE":ok=state.upgrade(0,Integer.parseInt(c[1]));break;
                case "TRAIN":ok=state.recruit(0,c[1]);break;
                case "MOVE":ok=state.move(0,Integer.parseInt(c[1]),Integer.parseInt(c[2]),Integer.parseInt(c[3]));break;
                default:break;
            }}catch(Exception ignored){}
            if(!ok)toast("Order rejected: check resources, placement or prerequisites.");view.invalidate();return ok;
        }
        if(server!=null)return server.command(order);
        return client!=null&&client.command(order);
    }
    private void configure(String field,String val){command("CFG|"+field+"|"+val);}
    private boolean amHost(){return server!=null;}
    private void takeoverDialog(){if(server==null||state==null)return;
        ArrayList<GameCore.Player> absent=new ArrayList<>();for(GameCore.Player p:state.players.values())if(!p.bot&&!p.connected)absent.add(p);
        if(absent.isEmpty()){toast("No disconnected players");return;}
        String[] names=new String[absent.size()];for(int i=0;i<names.length;i++)names[i]=absent.get(i).name+(absent.get(i).ai?" [AI enabled]":" [AI disabled]");
        new AlertDialog.Builder(this).setTitle("Host: AI control for disconnected faction")
            .setItems(names,(d,n)->{GameCore.Player p=absent.get(n);command("AI_TAKEOVER|"+p.id+"|"+(p.ai?"0":"1"));})
            .setNegativeButton("Cancel",null).show();
    }
    private void actionChoice(String choice){if(state==null||!state.phase.equals("PLAY"))return;
        if(Arrays.asList(GameCore.BUILDINGS).contains(choice)){
            placement=placement.equals(choice)?"":choice;info="Tap a free tile close to your HQ to build "+choice;
        }else if(Arrays.asList(GameCore.TROOPS).contains(choice)){command("TRAIN|"+choice);placement="";}
        else if(choice.equals("UPGRADE")){if(selectedBuilding<0)toast("Select one of your buildings first");else command("UPGRADE|"+selectedBuilding);}
        else if(choice.equals("AI CONTROL")){if(amHost())takeoverDialog();else toast("Only the host can assign AI takeover");}
        view.invalidate();
    }
    private void mapTapped(float sx,float sy){if(state==null||!state.phase.equals("PLAY"))return;
        // The hit test is the exact inverse of the diamond-centre isometric projection.
        final float centerX=310f, startY=244f, tw=40f, th=30f;
        float a=(sx-centerX)/(tw/2f),b=(sy-startY)/(th/2f);
        int col=Math.round((a+b)/2f),row=Math.round((b-a)/2f);
        if(!GameCore.in(col,row))return;
        if(!placement.isEmpty()){command("BUILD|"+placement+"|"+col+"|"+row);placement="";view.invalidate();return;}
        for(GameCore.Unit u:state.units)if(u.owner==myId&&u.x==col&&u.y==row){
            selectedUnit=u.id;selectedBuilding=-1;info=u.type+" selected • tap destination";view.invalidate();return;}
        for(GameCore.Building bld:state.buildings)if(bld.owner==myId&&bld.x==col&&bld.y==row){
            selectedBuilding=bld.id;selectedUnit=-1;info=bld.type+" Lv"+bld.level+" • select UPGRADE";view.invalidate();return;}
        if(selectedUnit>=0){command("MOVE|"+selectedUnit+"|"+col+"|"+row);info="Movement order sent";}
        else info="Select a unit, or tap BUILD then a nearby tile";
        view.invalidate();
    }

    private final class GameView extends View {
        private final Paint p=new Paint(3);
        private final ArrayList<Hit> hits=new ArrayList<>();
        private final int BG=0xFF071423,PA=0xFF10293A,PL=0xFF163749,LINE=0xFF2E6977,
            WHITE=0xFFE6F9FA,MUTED=0xFF95B8C5,CYAN=0xFF50E5E4,GOLD=0xFFFFBE4D,RED=0xFFFF666B,GREEN=0xFF63E8A9;
        private final int[] faction={0xFF3EB5FF,0xFFFF666B,0xFF66DD8E,0xFFFFCF4C,0xFFBC84FF,0xFF5FDFDB};
        private static final float ISOX=310f,ISOY=244f,TW=40f,TH=30f;
        private int backdropShift=0;
        GameView(Context context){super(context);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        private void fill(Canvas c,int color){c.drawColor(color);}
        private void rect(Canvas c,float x,float y,float w,float h,int col,float rad){p.setColor(col);p.setStyle(Paint.Style.FILL);c.drawRoundRect(new RectF(x,y,x+w,y+h),rad,rad,p);}
        private void stroke(Canvas c,float x,float y,float w,float h,int col,float rad,float thick){p.setColor(col);p.setStrokeWidth(thick);p.setStyle(Paint.Style.STROKE);c.drawRoundRect(new RectF(x,y,x+w,y+h),rad,rad,p);p.setStyle(Paint.Style.FILL);}
        private void ln(Canvas c,float x,float y,float xx,float yy,int col,float thick){p.setStyle(Paint.Style.STROKE);p.setColor(col);p.setStrokeWidth(thick);c.drawLine(x,y,xx,yy,p);p.setStyle(Paint.Style.FILL);}
        private void txt(Canvas c,String s,float x,float base,float size,int col,boolean bold){p.setStyle(Paint.Style.FILL);
            p.setColor(col);p.setTypeface(Typeface.create(bold?"sans-serif-condensed":"sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));p.setTextSize(size);c.drawText(s,x,base,p);}
        private void center(Canvas c,String s,float x,float base,float size,int col,boolean bold){p.setTextSize(size);p.setTypeface(Typeface.create("sans-serif-condensed",bold?Typeface.BOLD:Typeface.NORMAL));txt(c,s,x-p.measureText(s)/2f,base,size,col,bold);}
        private void circ(Canvas c,float x,float y,float r,int col){p.setStyle(Paint.Style.FILL);p.setColor(col);c.drawCircle(x,y,r,p);}
        private void diamond(Canvas c,float x,float y,float w,float h,int col){Path path=new Path();path.moveTo(x,y-h/2);path.lineTo(x+w/2,y);path.lineTo(x,y+h/2);path.lineTo(x-w/2,y);path.close();p.setColor(col);p.setStyle(Paint.Style.FILL);c.drawPath(path,p);}
        private void prism(Canvas c,float x,float y,float w,float h,float depth,int top,int left,int right){
            Path l=new Path();l.moveTo(x-w/2,y);l.lineTo(x,y+h/2);l.lineTo(x,y+h/2+depth);l.lineTo(x-w/2,y+depth);l.close();p.setColor(left);c.drawPath(l,p);
            Path r=new Path();r.moveTo(x+w/2,y);r.lineTo(x,y+h/2);r.lineTo(x,y+h/2+depth);r.lineTo(x+w/2,y+depth);r.close();p.setColor(right);c.drawPath(r,p);diamond(c,x,y,w,h,top);
        }
        private void btn(Canvas c,String s,float x,float y,float w,float h,boolean special,Runnable onClick){
            rect(c,x,y,w,h,special?0xFF176364:PA,11);stroke(c,x,y,w,h,special?CYAN:LINE,11,special?2.1f:1.4f);
            center(c,s,x+w/2,y+h/2+7,22,special?WHITE:WHITE,true);
            if(onClick!=null)hits.add(new Hit(new RectF(x,y,x+w,y+h),onClick));
        }
        private void small(Canvas c,String s,float x,float y,float w,float h,Runnable a){btn(c,s,x,y,w,h,false,a);}
        private void panel(Canvas c,float x,float y,float w,float h){rect(c,x,y,w,h,PA,15);stroke(c,x,y,w,h,LINE,15,1.1f);}
        private void grid(Canvas c){for(int i=0;i<=15;i++){ln(c,i*52,0,i*52,1280,0x152A5B73,1);ln(c,0,i*94,720,i*94,0x13285F7B,1);}}
        private void header(Canvas c,String kicker){rect(c,0,0,720,96,0xFF081C2C,0);ln(c,0,95,720,95,LINE,2);
            txt(c,"◆",24,62,40,CYAN,true);txt(c,"OREFRONT",78,54,35,WHITE,true);txt(c,"AI COMMAND WARS",81,81,17,GOLD,true);
            txt(c,kicker,472,57,15,CYAN,true);}
        private void tinyCircuit(Canvas c,float ox,float oy){for(int i=0;i<8;i++){
            float y=oy+i*47;ln(c,ox,y,ox+105+i%3*19,y,0x2C3C7992,1.6f);circ(c,ox+105+i%3*19,y,3,0xFF2F7587);}}
        private void creatureBadge(Canvas c,int who,float x,float y,int r){circ(c,x,y,r+4,0xFF162E40);circ(c,x,y,r,faction[who%6]);center(c,"P"+(who+1),x,y+6,r>16?18:13,0xFF03121E,true);}
        private static final class Hit {final RectF zone;final Runnable action;Hit(RectF z,Runnable a){zone=z;action=a;}}
        @Override protected void onDraw(Canvas c){super.onDraw(c);c.save();c.scale(getWidth()/720f,getHeight()/1280f);fill(c,BG);grid(c);hits.clear();
            if(page==Page.HOME)home(c);else if(page==Page.LOBBY)lobby(c);else battle(c);
            if(info!=null&&!info.isEmpty()){rect(c,14,1235,692,34,0xEE17374A,9);txt(c,info.substring(0,Math.min(info.length(),72)),26,1258,16,GOLD,false);}
            c.restore();}
        private void home(Canvas c){header(c,"TACTICAL RTS");
            // Hero artwork made of lightweight vector shapes, not a copied game sprite.
            rect(c,20,115,680,360,0xFF0B2A3C,16);stroke(c,20,115,680,360,0xFF246C7F,16,2);
            tinyCircuit(c,37,160);tinyCircuit(c,530,177);
            circ(c,360,253,112,0xFF123E55);circ(c,360,253,78,0xFF17536A);
            diamond(c,360,282,200,112,0xFF2B6982);prism(c,360,258,122,67,34,0xFF244C64,0xFF103246,0xFF152A41);
            rect(c,340,185,40,79,0xFFABDBE8,6);rect(c,323,178,74,30,0xFF1B455B,8);
            circ(c,360,187,16,0xFF3DD5E7);ln(c,296,327,425,327,0xFF37BFE0,4);
            center(c,"COMMAND THE FRONTIER",360,386,31,WHITE,true);
            center(c,"6 FACTIONS  •  LAND / AIR  •  SMART AI",360,419,18,CYAN,true);
            center(c,"Build bases. Control sectors. Win the war.",360,447,18,MUTED,false);
            panel(c,20,493,680,256);
            txt(c,"CHOOSE YOUR BATTLE",41,530,23,WHITE,true);
            btn(c,"◈   PLAY AGAINST AI",44,547,632,78,true,()->startAI());
            rect(c,44,645,632,79,0xFF503920,12);stroke(c,44,645,632,79,GOLD,12,2);
            center(c,"⌘   LOCAL LAN MULTIPLAYER",360,694,23,WHITE,true);
            hits.add(new Hit(new RectF(44,645,676,724),()->chooseName(()->host())));
            panel(c,20,766,680,208);txt(c,"COMMANDER PROFILE",42,806,23,WHITE,true);
            creatureBadge(c,0,106,882,38);txt(c,username,169,858,30,WHITE,true);txt(c,"RANK  •  FIELD COMMANDER",169,888,18,GOLD,true);
            txt(c,"Mode: offline AI or 2–6 local players",42,932,17,MUTED,false);
            panel(c,20,993,680,221);
            txt(c,"MULTIPLAYER OPERATIONS",40,1033,23,CYAN,true);
            btn(c,"SEARCH LAN ROOMS",42,1047,306,63,false,()->searchRooms());
            btn(c,"JOIN BY CODE + IP",366,1047,310,63,false,()->chooseName(()->manualJoin()));
            txt(c,"No MacBook host. No internet for hotspot matches.",42,1146,17,MUTED,false);
            txt(c,"This version uses original lightweight vector graphics.",42,1184,16,MUTED,false);
        }
        private void lobby(Canvas c){GameCore w=state;if(w==null){header(c,"CONNECTING");center(c,"Opening LAN lobby...",360,560,30,WHITE,true);return;}
            header(c,amHost()?"HOST ONLINE":"LAN CLIENT");
            panel(c,18,113,684,176);txt(c,"LOCAL LAN ROOM • MAX SIX HUMANS",40,151,22,CYAN,true);
            txt(c,"ROOM CODE",41,199,17,MUTED,true);center(c,w.roomCode,360,214,45,GOLD,true);
            txt(c,"HOST IP: "+(hostIP.isEmpty()?"Connecting":hostIP),41,258,18,MUTED,false);
            btn(c,"LEAVE",565,235,114,38,false,()->leaveRoom());
            panel(c,18,303,684,444);txt(c,"COMMANDERS  "+w.connectedHumans()+" / "+w.maxPlayers,39,342,25,WHITE,true);
            txt(c,"Tap a player for host team / faction selection",39,367,16,MUTED,false);
            for(int i=0;i<6;i++){final int idx=i;float yy=380+i*56;
                rect(c,34,yy,652,51,selectedPlayer==i?0xFF1E4C56:0xFF112D3D,8);
                GameCore.Player pl=w.player(i);
                if(pl!=null){creatureBadge(c,i,63,yy+26,18);
                    txt(c,pl.name+(i==0?"  ★ HOST":""),91,yy+23,18,WHITE,true);
                    txt(c,GameCore.FACTIONS[pl.faction]+"  •  TEAM "+(pl.team+1),91,yy+42,14,MUTED,false);
                    String status=pl.bot?"BOT":!pl.connected?"OFFLINE":pl.ready?"READY":"WAIT";
                    txt(c,status,575,yy+30,17,pl.ready?GREEN:GOLD,true);
                }else txt(c,"SLOT "+(i+1)+"  •  AVAILABLE",93,yy+33,17,MUTED,false);
                hits.add(new Hit(new RectF(35,yy,685,yy+51),()->{selectedPlayer=idx;invalidate();}));
            }
            panel(c,18,759,684,340);txt(c,"HOST MATCH SETTINGS",38,797,22,CYAN,true);
            String[] labels={"PLAYERS","TYPE","BATTLEFIELD","DURATION","RESOURCES","AI LEVEL","AI SLOTS"};
            String[] vals={w.maxPlayers+"",w.mode,GameCore.MAPS[w.map],w.matchMinutes+" MIN",
                new String[]{"STANDARD","BOOSTED","HIGH"}[w.startResources],new String[]{"EASY","MEDIUM","HARD"}[w.aiDifficulty],w.botCount+""};
            for(int i=0;i<labels.length;i++){final int ii=i;float yy=808+i*39;
                txt(c,labels[i],41,yy+26,17,MUTED,true);rect(c,291,yy,383,35,0xFF1D4455,7);
                center(c,vals[i]+(amHost()?"   ›":""),482,yy+25,17,WHITE,true);
                if(amHost())hits.add(new Hit(new RectF(291,yy,674,yy+35),()->cycleSetting(ii)));
            }
            if(amHost()){
                small(c,"TEAM +",32,1110,205,48,()->{
                    GameCore.Player p=w.player(selectedPlayer);if(p!=null)command("TEAM|"+p.id+"|"+((p.team+1)%6));
                    else toast("Select an occupied player; team editing needs CUSTOM mode");});
                small(c,"FACTION +",257,1110,215,48,()->{
                    GameCore.Player p=w.player(selectedPlayer);if(p!=null)command("FACTION|"+p.id+"|"+((p.faction+1)%6));});
            }
            GameCore.Player me=w.player(myId);
            btn(c,me!=null&&me.ready?"NOT READY":"I'M READY",34,1170,308,57,me!=null&&me.ready,()->command("READY|"+(w.player(myId)!=null&&w.player(myId).ready?"0":"1")));
            if(amHost())btn(c,"START MATCH",360,1170,326,57,true,()->command("START"));
            else txt(c,"Wait for the host to start",371,1206,19,GOLD,true);
            txt(c,"Phone 1 hosts. Phones 2–6 join over Wi-Fi / hotspot.",39,1249,17,MUTED,false);
        }
        private void cycleSetting(int i){if(state==null||!amHost())return;GameCore w=state;
            switch(i){case 0:configure("MAX",""+(w.maxPlayers>=6?2:w.maxPlayers+1));break;
                case 1:configure("MODE",GameCore.MODES[(Arrays.asList(GameCore.MODES).indexOf(w.mode)+1)%GameCore.MODES.length]);break;
                case 2:configure("MAP",""+((w.map+1)%3));break;
                case 3:configure("TIME",""+(w.matchMinutes==5?10:w.matchMinutes==10?15:w.matchMinutes==15?20:5));break;
                case 4:configure("RESOURCES",""+((w.startResources+1)%3));break;
                case 5:configure("AI",""+((w.aiDifficulty+1)%3));break;
                case 6:configure("BOTS",""+((w.botCount+1)%6));break;
                default:break;}}
        private void battle(Canvas c){GameCore w=state;if(w==null){home(c);return;}
            GameCore.Player me=w.player(myId);header(c,offline?"AI OFFLINE":"LAN "+w.roomCode);
            // Resource HUD: values are always local to this player, but the host syncs all players.
            rect(c,12,108,696,102,PA,14);stroke(c,12,108,696,102,LINE,14,1);
            if(me!=null){String[] names={"GOLD","OIL","IRON","ENERGY"};int[] resources={me.gold,me.oil,me.iron,me.energy};
                int[] col={GOLD,0xFF79D8FC,0xFFD0D6EB,CYAN};
                for(int i=0;i<4;i++){float x=23+i*173;txt(c,names[i],x,136,15,MUTED,true);
                    txt(c,""+resources[i],x,174,27,col[i],true);if(i<3)ln(c,x+161,122,x+161,191,LINE,1);}
                txt(c,"P"+(myId+1)+" • "+GameCore.FACTIONS[me.faction]+" | BASE "+me.baseHP+" HP",25,198,14,MUTED,true);}
            // Tactical battlefield frame and minimap.
            rect(c,12,217,696,546,0xFF0E3145,12);stroke(c,12,217,696,546,0xFF357584,12,1.8f);
            drawIsometricWorld(c,w);
            rect(c,24,727,430,28,0xE9102537,6);
            txt(c,"TAP UNIT  →  TAP TILE  •  HOLD YOUR TERRITORY",33,746,14,CYAN,true);
            drawMiniMap(c,w,557,650,129,106);
            int sec=w.matchMinutes*60-w.elapsedTicks/2;sec=Math.max(0,sec);
            rect(c,22,224,238,39,0xE70C2436,8);txt(c,"◷  "+(sec/60)+":"+String.format(Locale.US,"%02d",sec%60),34,251,21,GOLD,true);
            txt(c,"MAP: "+GameCore.MAPS[w.map],275,252,18,WHITE,true);
            // Under-map controls, refined from the concept mockup.
            panel(c,12,776,696,440);
            rect(c,25,789,674,86,0xFF153748,10);txt(c,"SELECTED UNIT / STRUCTURE",43,816,16,CYAN,true);
            String sel="COMMAND CENTER";String detail="Select one of your units or buildings on the map";
            if(selectedUnit>=0)for(GameCore.Unit u:w.units)if(u.id==selectedUnit&&u.owner==myId){sel=u.type+"  •  HP "+u.hp;detail="Target: "+u.tx+","+u.ty+"  |  Tap another tile to move";}
            if(selectedBuilding>=0)for(GameCore.Building b:w.buildings)if(b.id==selectedBuilding&&b.owner==myId){sel=b.type+"   LV "+b.level;detail="HP "+b.hp+"  •  Tap UPGRADE to improve";}
            txt(c,sel,41,843,25,WHITE,true);txt(c,detail,42,863,15,MUTED,false);
            String[] tabs={"BUILD","UNITS","UPGRADE"};for(int i=0;i<3;i++){final int index=i;
                btn(c,tabs[i],25+i*228,891,214,51,tab==i,()->{tab=index;placement="";invalidate();});}
            if(tab==0){String[] items={"MINE","REFINERY","GENERATOR","BARRACKS","FACTORY","AIRFIELD","TURRET"};
                for(int i=0;i<items.length;i++){final String item=items[i];int col=i%3,row=i/3;
                    btn(c,item,25+col*228,951+row*55,214,49,item.equals(placement),()->actionChoice(item));}
            }else if(tab==1){String[] troops={"SOLDIER","TANK","AIRCRAFT","HERO"};
                for(int i=0;i<troops.length;i++){final String item=troops[i];btn(c,item,25+(i%2)*344,959+(i/2)*63,330,54,false,()->actionChoice(item));}
                txt(c,"Barracks → Soldiers | Factory → Tanks | Airfield → Aircraft",29,1111,15,MUTED,false);
            }else{btn(c,"UPGRADE SELECTED BUILDING",31,970,654,66,true,()->actionChoice("UPGRADE"));
                txt(c,"Upgrades improve structure HP and resource output.",34,1072,17,MUTED,false);
                if(amHost())small(c,"AI TAKEOVER FOR DISCONNECTED PLAYER",35,1091,649,49,()->actionChoice("AI CONTROL"));}
            String hint=!placement.isEmpty()?"PLACE "+placement+" • TAP A FREE TILE BY YOUR BASE":
                "AI INTEL • "+((offline||amHost())?w.intelligence(myId):intel);
            rect(c,18,1221,684,47,0xFF12384B,9);
            txt(c,hint.substring(0,Math.min(hint.length(),65)),27,1250,16,GOLD,true);
            if(!w.phase.equals("PLAY")){
                rect(c,51,427,619,233,0xF10A2335,16);stroke(c,51,427,619,233,GOLD,16,2);
                center(c,"MATCH FINISHED",360,486,37,GOLD,true);
                center(c,w.result,360,539,23,WHITE,true);
                btn(c,"RETURN TO MENU",101,564,518,68,true,()->leaveRoom());
            }
            btn(c,"EXIT",575,18,125,57,false,()->new AlertDialog.Builder(MainActivity.this)
                .setTitle("Leave match?").setMessage("Disconnecting keeps your faction at the host until the game ends.")
                .setNegativeButton("Cancel",null).setPositiveButton("Leave",(d,v)->leaveRoom()).show());
        }
        private void drawMiniMap(Canvas c,GameCore w,float x,float y,float width,float height){
            rect(c,x,y,width,height,0xEC081D2C,6);stroke(c,x,y,width,height,LINE,6,1);
            for(GameCore.Player pl:w.players.values())if(pl.baseHP>0){float px=x+(pl.baseX+0.5f)/GameCore.W*width;
                float py=y+(pl.baseY+0.5f)/GameCore.H*height;circ(c,px,py,5,faction[pl.id]);}
            for(GameCore.Sector s:w.sectors){float px=x+(s.x+.5f)/GameCore.W*width;
                float py=y+(s.y+.5f)/GameCore.H*height;circ(c,px,py,2.4f,s.owner<0?GOLD:faction[s.owner]);}
            txt(c,"N",x+width-16,y+16,12,WHITE,true);
        }
        private float px(int x,int y){return ISOX+(x-y)*(TW/2f);}
        private float py(int x,int y){return ISOY+(x+y)*(TH/2f);}
        private void drawIsometricWorld(Canvas c,GameCore w){
            // Per-tile depth sorting makes raised walls, buildings, aircraft and infantry overlap correctly.
            for(int sum=0;sum<GameCore.W+GameCore.H-1;sum++)for(int x=0;x<GameCore.W;x++){
                int y=sum-x;if(y<0||y>=GameCore.H)continue;
                float xx=px(x,y),yy=py(x,y);boolean rock=w.rock[x][y];
                boolean river=(!rock&&x>4&&x<15&&((y==5&&x%4!=0)||(y==6&&x%4==0)));
                int base=rock?0xFF4B6070:river?0xFF226782:((x*7+y*11+w.map*13)%4==0)?0xFF5B8367:0xFF3E705E;
                if(w.map==1&&!rock&&!river)base=((x+y)%3==0)?0xFF98784F:0xFF8B734E;
                if(w.map==2&&!rock&&!river)base=((x+y)%3==0)?0xFF337B69:0xFF437B67;
                diamond(c,xx,yy,TW,TH,base);
                if(rock)prism(c,xx,yy-4,TW-8,TH-7,8,0xFF8093A0,0xFF3A5266,0xFF2E4157);
                else if(river){ln(c,xx-5,yy,xx+8,yy,0xFF62A3BD,1.4f);}
                else if((x*23+y*37+17)%19==3){ // deterministic vector trees
                    rect(c,xx-2,yy-16,4,19,0xFF58452D,1);diamond(c,xx,yy-22,17,24,0xFF2C9A75);
                }
                for(GameCore.Sector s:w.sectors)if(s.x==x&&s.y==y){
                    diamond(c,xx,yy,TW,TH,s.owner<0?0xFFBE8E31:faction[s.owner]);
                    circ(c,xx,yy-13,6,s.owner<0?GOLD:WHITE);txt(c,"◇",xx-6,yy-17,14,0xFF18313B,true);
                }
                for(GameCore.Player pl:w.players.values())if(pl.baseHP>0&&pl.baseX==x&&pl.baseY==y)drawHQ(c,xx,yy,pl);
                for(GameCore.Building b:w.buildings)if(b.x==x&&b.y==y)drawBuilding(c,xx,yy,b);
                for(GameCore.Unit u:w.units)if(u.x==x&&u.y==y)drawUnit(c,xx,yy,u);
            }
        }
        private void drawHQ(Canvas c,float x,float y,GameCore.Player pl){
            int f=faction[pl.id];prism(c,x,y-16,38,27,16,f,0xFF192C40,0xFF243A54);
            rect(c,x-11,y-46,22,23,f,4);rect(c,x-5,y-57,10,14,0xFFB9EDF7,2);
            ln(c,x+12,y-44,x+19,y-65,0xFFBED9ED,2);diamond(c,x+19,y-65,11,7,GOLD);
            circ(c,x,y-12,5,WHITE);txt(c,"P"+(pl.id+1),x-10,y+29,15,WHITE,true);
            rect(c,x-18,y+13,36,4,0xFF4D2633,3);rect(c,x-18,y+13,36*pl.baseHP/260f,4,GREEN,3);
        }
        private void drawBuilding(Canvas c,float x,float y,GameCore.Building b){int f=faction[b.owner];
            prism(c,x,y-9,31,22,10,0xFFB3D1D7,0xFF34465F,0xFF1B3850);
            rect(c,x-11,y-34,22,19,0xFF39566F,2);
            if(b.type.equals("MINE")){diamond(c,x,y-31,19,11,GOLD);}
            else if(b.type.equals("REFINERY")){circ(c,x+7,y-31,7,0xFF3F97C0);rect(c,x-9,y-45,4,17,0xFF7796AB,1);}
            else if(b.type.equals("GENERATOR")){rect(c,x-5,y-49,10,16,CYAN,2);diamond(c,x,y-49,19,11,WHITE);}
            else if(b.type.equals("BARRACKS")){rect(c,x-9,y-45,19,8,f,2);}
            else if(b.type.equals("FACTORY")){rect(c,x-6,y-45,17,16,0xFF7F9BAC,1);}
            else if(b.type.equals("AIRFIELD")){ln(c,x-14,y-22,x+15,y-22,CYAN,3);}
            else if(b.type.equals("TURRET")){circ(c,x,y-31,8,0xFF566F8A);ln(c,x,y-31,x+14,y-41,f,5);}
            ln(c,x-16,y-14,x+16,y-14,f,2.5f);
            if(selectedBuilding==b.id){diamond(c,x,y+8,48,24,0x553DE6E8);txt(c,"LV"+b.level,x-11,y+29,12,GOLD,true);}
        }
        private void drawUnit(Canvas c,float x,float y,GameCore.Unit u){int f=faction[u.owner];
            float uy=y-11;if(selectedUnit==u.id)diamond(c,x,uy+9,41,21,0xAAFFD85C);
            if(u.type.equals("SOLDIER")){circ(c,x,uy-15,6,0xFFD6E7EA);rect(c,x-5,uy-9,10,13,f,2);ln(c,x+4,uy-8,x+13,uy-14,0xFF233044,3);}
            else if(u.type.equals("TANK")){rect(c,x-12,uy-15,24,16,f,4);rect(c,x-7,uy-20,14,11,0xFF7599AC,3);ln(c,x,uy-19,x+15,uy-27,0xFF21364C,5);}
            else if(u.type.equals("AIRCRAFT")){diamond(c,x,uy-26,39,12,0xFFD0EAF5);ln(c,x,uy-27,x+3,uy-47,0xFF6290AD,5);}
            else{circ(c,x,uy-21,9,GOLD);rect(c,x-9,uy-15,18,20,f,5);ln(c,x-11,uy-22,x+13,uy-22,0xFFFFE1AA,3);}
            txt(c,""+u.hp,x-10,uy+21,11,WHITE,true);
        }
        @Override public boolean onTouchEvent(MotionEvent ev){if(ev.getAction()!=MotionEvent.ACTION_UP)return true;
            float x=ev.getX()*720f/getWidth(),y=ev.getY()*1280f/getHeight();
            for(int i=hits.size()-1;i>=0;i--){Hit hit=hits.get(i);if(hit.zone.contains(x,y)){hit.action.run();invalidate();return true;}}
            if(page==Page.BATTLE&&x>=14&&x<=709&&y>=217&&y<=762)mapTapped(x,y);
            return true;
        }
    }
}
