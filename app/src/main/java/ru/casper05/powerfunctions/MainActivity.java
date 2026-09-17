package ru.casper05.powerfunctions;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.hardware.ConsumerIrManager;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class MainActivity extends Activity {
    private ConsumerIrManager ir;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int channel = 1, red = 0, blue = 0;
    private boolean redForward = true, blueForward = true;
    private static final int CARRIER = 38000;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        ir = (ConsumerIrManager)getSystemService(Context.CONSUMER_IR_SERVICE);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24,18,24,18);
        root.setBackgroundColor(Color.rgb(18,18,18));
        TextView title=text("POWER FUNCTIONS",24); title.setGravity(Gravity.CENTER); root.addView(title,lp(-1,60));
        TextView status=text(ir!=null&&ir.hasIrEmitter()?"IR: ready":"IR: transmitter not detected",14); status.setGravity(Gravity.CENTER); root.addView(status,lp(-1,38));
        LinearLayout ch=new LinearLayout(this); ch.setGravity(Gravity.CENTER);
        ch.addView(text("Channel",16),lp(100,55));
        Spinner spinner=new Spinner(this); String[] channels={"1","2","3","4"};
        spinner.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,channels));
        spinner.setSelection(channel-1); spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> p){}
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){channel=pos+1;}
        }); ch.addView(spinner,lp(90,55)); root.addView(ch);
        root.addView(makeControl("RED / A",true),new LinearLayout.LayoutParams(-1,0,1));
        root.addView(makeControl("BLUE / B",false),new LinearLayout.LayoutParams(-1,0,1));
        Button stop=new Button(this); stop.setText("STOP ALL"); stop.setTextSize(18); stop.setOnClickListener(v->{red=0;blue=0;send();}); root.addView(stop,lp(-1,60));
        setContentView(root);
    }

    private View makeControl(String name,boolean isRed){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(0,4,0,4);
        TextView label=text(name+"   STOP",18); label.setGravity(Gravity.CENTER);
        SeekBar bar=new SeekBar(this); bar.setMax(7); bar.setProgress(0);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){send();}
            public void onProgressChanged(SeekBar s,int p,boolean from){
                if(isRed)red=p;else blue=p;
                boolean f=isRed?redForward:blueForward;
                label.setText(name+"   "+(p==0?"STOP":(p+(f?" →":" ←"))));
                if(from)send();
            }
        });
        LinearLayout dirs=new LinearLayout(this); Button l=new Button(this),r=new Button(this);
        l.setText("◀");r.setText("▶");
        l.setOnClickListener(v->{if(isRed)redForward=false;else blueForward=false;send();});
        r.setOnClickListener(v->{if(isRed)redForward=true;else blueForward=true;send();});
        dirs.addView(l,lp(0,52,1));dirs.addView(r,lp(0,52,1));
        box.addView(label,lp(-1,40));box.addView(bar,lp(-1,60));box.addView(dirs);
        return box;
    }

    private void send(){
        if(ir==null||!ir.hasIrEmitter())return;
        int a=powerValue(red,redForward), b=powerValue(blue,blueForward);
        // Combo PWM: nibble 1 = address(0), escape(1), channel(0..3).
        int n1=0x4|(channel-1), n2=b, n3=a, lrc=(0xF^n1^n2^n3)&0xF;
        int raw=(n1<<12)|(n2<<8)|(n3<<4)|lrc;
        handler.post(() -> transmitFive(raw,channel-1));
    }

    private void transmitFive(int raw,int ch){
        if(ir==null||!ir.hasIrEmitter())return;
        int[] pattern=LegoIr.frame(raw);
        long frameMs=12;
        try{Thread.sleep((3-ch)*16L);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}
        for(int i=0;i<5;i++){
            ir.transmit(CARRIER,pattern);
            if(i<4){try{Thread.sleep(Math.max(1,(110+40L*ch)-frameMs));}catch(InterruptedException e){Thread.currentThread().interrupt();return;}}
        }
    }

    private int powerValue(int level,boolean forward){
        if(level<=0)return 0;
        int v=Math.min(level,7);
        return forward?v:(16-v); // reverse 7..1 => 9..15
    }

    private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(size);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w,h);}
    private LinearLayout.LayoutParams lp(int w,int h,float weight){return new LinearLayout.LayoutParams(w,h,weight);}

    static class LegoIr {
        static int[] frame(int raw){
            // 38 kHz: 6-cycle mark = 158 us; 10-cycle zero space = 263 us;
            // 21-cycle one space = 553 us; start/stop space = 1026 us.
            int[] out=new int[36]; int k=0;
            out[k++]=158; out[k++]=1026;
            for(int i=15;i>=0;i--){int bit=(raw>>i)&1;out[k++]=158;out[k++]=(bit==1?553:263);}
            out[k++]=158; out[k++]=1026;
            return out;
        }
    }
}
