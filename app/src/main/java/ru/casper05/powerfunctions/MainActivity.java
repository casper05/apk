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
import android.widget.Toast;

public class MainActivity extends Activity {
    private ConsumerIrManager ir;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int channel = 1;
    private int red = 0, blue = 0;
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

        TextView title = text("POWER FUNCTIONS", 24); title.setGravity(Gravity.CENTER); root.addView(title, lp(-1,60));
        TextView status = text(ir != null && ir.hasIrEmitter() ? "IR: ready" : "IR: transmitter not detected", 14); status.setGravity(Gravity.CENTER); root.addView(status, lp(-1,38));

        LinearLayout ch = new LinearLayout(this); ch.setGravity(Gravity.CENTER);
        TextView ct = text("Channel",16); ch.addView(ct, lp(100,55));
        Spinner spinner = new Spinner(this); String[] channels={"1","2","3","4"};
        spinner.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, channels));
        spinner.setSelection(channel-1); spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){ public void onNothingSelected(android.widget.AdapterView<?> p){} public void onItemSelected(android.widget.AdapterView<?> p, View v,int pos,long id){channel=pos+1;}});
        ch.addView(spinner, lp(90,55)); root.addView(ch);

        root.addView(makeControl("RED / A", true), new LinearLayout.LayoutParams(-1,0,1));
        root.addView(makeControl("BLUE / B", false), new LinearLayout.LayoutParams(-1,0,1));
        Button stop = new Button(this); stop.setText("STOP ALL"); stop.setTextSize(18); stop.setOnClickListener(v->{red=0;blue=0;send();}); root.addView(stop,lp(-1,60));
        setContentView(root);
    }

    private View makeControl(String name, boolean isRed) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(0,4,0,4);
        TextView label=text(name+"   0",18); label.setGravity(Gravity.CENTER);
        SeekBar bar=new SeekBar(this); bar.setMax(7); bar.setProgress(0);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){ if(isRed) red=0; else blue=0; send(); }
            public void onProgressChanged(SeekBar s,int p,boolean from){ if(isRed) red=p; else blue=p; label.setText(name+"   "+(p==0?"STOP":(p+(isRed?(redForward?" →":" ←"):(blueForward?" →":" ←"))))); if(from) send(); }
        });
        bar.setOnTouchListener((v,e)->{ if(e.getAction()==MotionEvent.ACTION_DOWN){
                float x=e.getX(); if(x<bar.getWidth()/2){ if(isRed)redForward=false;else blueForward=false;} else {if(isRed)redForward=true;else blueForward=true;}
                bar.invalidate(); }
                return false; });
        box.addView(label,lp(-1,40)); box.addView(bar,lp(-1,60));
        LinearLayout dirs=new LinearLayout(this); Button l=new Button(this), r=new Button(this);
        l.setText("◀"); r.setText("▶"); l.setOnClickListener(v->{if(isRed)redForward=false;else blueForward=false;send();}); r.setOnClickListener(v->{if(isRed)redForward=true;else blueForward=true;send();});
        dirs.addView(l,lp(0,52,1)); dirs.addView(r,lp(0,52,1)); box.addView(dirs);
        return box;
    }

    private void send(){ if(ir==null||!ir.hasIrEmitter())return; int a=powerValue(red,redForward), b=powerValue(blue,blueForward); int nibble1=0x4, nibble2=channel-1, nibble3=a, nibble4=b; int cmd=(nibble1<<12)|(nibble2<<8)|(nibble3<<4)|nibble4; cmd = (cmd & 0xFFF0) | ((~(nibble1^nibble2^nibble3^nibble4)) & 0xF); int[] pattern=LegoIr.frame(cmd); ir.transmit(CARRIER,pattern); }
    private int powerValue(int level, boolean fwd){ if(level<=0)return 0; int v=Math.min(level,7); return fwd?v:(8+v); }

    private TextView text(String s,int size){ TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(size);t.setGravity(Gravity.CENTER_VERTICAL);return t; }
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w,h);}
    private LinearLayout.LayoutParams lp(int w,int h,float weight){return new LinearLayout.LayoutParams(w,h,weight);}

    static class LegoIr {
        static int[] frame(int cmd){
            int[] out=new int[1+2*17+1]; int k=0; out[k++]=158; for(int i=15;i>=0;i--){int bit=(cmd>>i)&1;out[k++]=26;out[k++]=bit==1?130:58;} out[k++]=26; return out;
        }
    }
}
