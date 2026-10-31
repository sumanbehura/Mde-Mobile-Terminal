package com.mde.mobileterminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;
import android.text.InputType;
import java.io.*;
import java.util.*;
import java.net.*;
import java.text.SimpleDateFormat;

public class MainActivity extends Activity {
    private final String BG="#10141D", PANEL="#171E2A", GREEN="#69E3A8", WHITE="#E7EDF5", MUTED="#9BA9BC";
    private LinearLayout screen;
    private TextView output, pathView, status;
    private EditText input;
    private Spinner modes;
    private File cwd;
    private final ArrayList<String> history = new ArrayList<>();

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        cwd=getExternalFilesDir(null); if(cwd==null) cwd=getFilesDir();
        build();
        print("MDE MOBILE TERMINAL  •  v1.0.0\nAndroid ARM64 • Hybrid terminal\nType help to list commands.\n");
        updatePath();
    }
    private TextView text(String s,int size,String color) {
        TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(Color.parseColor(color));
        t.setTypeface(Typeface.MONOSPACE); return t;
    }
    private Button btn(String s) {
        Button b=new Button(this); b.setText(s); b.setTextSize(11); b.setAllCaps(false); b.setTextColor(Color.parseColor(GREEN)); return b;
    }
    private void build() {
        screen=new LinearLayout(this); screen.setOrientation(1); screen.setPadding(14,12,14,10); screen.setBackgroundColor(Color.parseColor(BG)); setContentView(screen);
        LinearLayout head=new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("MDE  /  MOBILE TERMINAL",17,GREEN); title.setTypeface(null,1); head.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button connect=btn("REMOTE"); connect.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Remote Windows")
            .setMessage("Remote Windows execution requires a configured SSH client and a trusted host key. It is not enabled in this initial build. Use Android Shell or local CMD compatibility for now.")
            .setPositiveButton("OK",null).show()); head.addView(connect); screen.addView(head);
        status=text("LOCAL DEVICE  •  READY",11,MUTED); status.setPadding(2,8,2,8); screen.addView(status);
        modes=new Spinner(this);
        String[] choices={"Windows CMD compatibility","PowerShell compatibility","Android Shell"};
        modes.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,choices));
        screen.addView(modes,new LinearLayout.LayoutParams(-1,48));
        pathView=text("",11,GREEN); pathView.setPadding(2,8,2,8); screen.addView(pathView);
        ScrollView sc=new ScrollView(this); sc.setFillViewport(true);
        LinearLayout panel=new LinearLayout(this); panel.setPadding(10,12,10,12); panel.setBackgroundColor(Color.parseColor("#0B0F16"));
        output=text("",13,WHITE); output.setTextIsSelectable(true); output.setLineSpacing(3,1f); panel.addView(output); sc.addView(panel);
        screen.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout row=new LinearLayout(this); row.setPadding(0,10,0,0); row.setGravity(Gravity.CENTER_VERTICAL);
        input=new EditText(this); input.setSingleLine(true); input.setTextSize(14); input.setTypeface(Typeface.MONOSPACE);
        input.setTextColor(Color.parseColor(WHITE)); input.setHintTextColor(Color.parseColor(MUTED)); input.setHint("Enter command…");
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setBackgroundColor(Color.parseColor(PANEL)); input.setOnEditorActionListener((v,a,e)->{run();return true;});
        row.addView(input,new LinearLayout.LayoutParams(0,52,1));
        Button run=btn("RUN"); run.setOnClickListener(v->run()); row.addView(run); screen.addView(row);
        LinearLayout foot=new LinearLayout(this); foot.setGravity(Gravity.CENTER);
        Button clear=btn("CLEAR"); clear.setOnClickListener(v->output.setText(""));
        Button help=btn("HELP"); help.setOnClickListener(v->print(help()));
        Button info=btn("INFO"); info.setOnClickListener(v->print(deviceInfo()));
        foot.addView(clear);foot.addView(help);foot.addView(info);screen.addView(foot);
    }
    private void print(String s) { output.append(s); final ScrollView sc=(ScrollView)output.getParent().getParent(); sc.post(()->sc.fullScroll(ScrollView.FOCUS_DOWN)); }
    private void updatePath(){pathView.setText("WORKSPACE  "+cwd.getAbsolutePath());}
    private void run() {
        String cmd=input.getText().toString().trim(); if(cmd.isEmpty())return;
        input.setText(""); history.add(cmd);
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);
        String prompt=modes.getSelectedItemPosition()==2?"$ ":modes.getSelectedItemPosition()==1?"PS> ":"C:\\Users\\MDE> ";
        print("\n"+prompt+cmd+"\n");
        if(modes.getSelectedItemPosition()==2) new Thread(()->{String r=shell(cmd);runOnUiThread(()->print(r+"\n"));}).start();
        else print(windows(cmd)+"\n");
    }
    private File resolve(String p){File f=new File(unquote(p));return f.isAbsolute()?f:new File(cwd,p);}
    private String unquote(String s){if(s.length()>1&&((s.startsWith("\"")&&s.endsWith("\""))||(s.startsWith("'")&&s.endsWith("'"))))return s.substring(1,s.length()-1);return s;}
    private String windows(String raw) {
        String c=raw.trim(), l=c.toLowerCase(Locale.ROOT);
        if(l.equals("help")||l.equals("/?"))return help();
        if(l.equals("cls")||l.equals("clear")){output.setText("");return "";}
        if(l.equals("pwd")||l.equals("cd"))return cwd.getAbsolutePath();
        if(l.equals("dir")||l.equals("ls")||l.equals("get-childitem")){
            File[] fs=cwd.listFiles(); if(fs==null)return "Directory unavailable.";
            StringBuilder b=new StringBuilder("Directory of "+cwd+"\n\n");
            for(File f:fs)b.append(f.isDirectory()?"<DIR>       ":"            ").append(f.getName()).append(f.isFile()?"  ("+f.length()+" bytes)":"").append("\n");
            return b.toString();
        }
        if(l.startsWith("cd ")){File f=resolve(c.substring(3).trim());if(f.isDirectory()){cwd=f;updatePath();return "";}return "The system cannot find the path specified.";}
        if(l.startsWith("mkdir ")||l.startsWith("md ")){File f=resolve(c.substring(c.indexOf(' ')+1).trim());return f.mkdirs()?"Directory created.":"Could not create directory.";}
        if(l.startsWith("echo "))return c.substring(5);
        if(l.startsWith("type ")||l.startsWith("cat ")){
            File f=resolve(c.substring(c.indexOf(' ')+1).trim());try{return read(f);}catch(Exception e){return "Read error: "+e.getMessage();}
        }
        if(l.startsWith("del ")||l.startsWith("erase ")||l.startsWith("rm ")){
            File f=resolve(c.substring(c.indexOf(' ')+1).trim());return f.delete()?"Deleted.":"Delete failed.";
        }
        if(l.equals("ver"))return "MDE Windows compatibility layer [Android]";
        if(l.equals("whoami"))return "android\\mde";
        if(l.equals("hostname"))return "MDE-ANDROID";
        if(l.equals("systeminfo")||l.equals("get-computerinfo"))return deviceInfo();
        if(l.equals("ipconfig"))return networkInfo();
        if(l.equals("date"))return new Date().toString();
        if(l.equals("time"))return new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date());
        if(l.startsWith("copy ")||l.startsWith("move ")){
            String[] a=c.substring(c.indexOf(' ')+1).split("\\s+",2);if(a.length<2)return "Usage: copy/move <source> <destination>";
            File src=resolve(a[0]),dst=resolve(a[1]);
            try{if(l.startsWith("move ")){return src.renameTo(dst)?"Moved.":"Move failed.";}
                try(InputStream in=new FileInputStream(src);OutputStream out=new FileOutputStream(dst)){byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)out.write(buf,0,n);}return "1 file(s) copied.";
            }catch(Exception e){return "Operation failed: "+e.getMessage();}
        }
        return "'"+c+"' is not recognized by the local compatibility interpreter. Type help. For genuine Windows commands, use a Windows PC remote shell (planned).";
    }
    private String shell(String cmd){
        try{Process p=new ProcessBuilder("/system/bin/sh","-c",cmd).directory(cwd).redirectErrorStream(true).start();
            ByteArrayOutputStream out=new ByteArrayOutputStream();InputStream in=p.getInputStream();byte[] b=new byte[4096];int n;
            while((n=in.read(b))!=-1)out.write(b,0,n);p.waitFor();return out.toString("UTF-8");
        }catch(Exception e){return "Shell error: "+e.getMessage();}
    }
    private String read(File f)throws Exception{
        if(!f.isFile())return "File not found.";if(f.length()>1048576)return "File exceeds 1 MB display limit.";
        return new String(java.nio.file.Files.readAllBytes(f.toPath()),"UTF-8");
    }
    private String deviceInfo(){return "Host Name: MDE-ANDROID\nOS: Android "+android.os.Build.VERSION.RELEASE+"\nArchitecture: "+android.os.Build.SUPPORTED_ABIS[0]+"\nWindows mode: local command compatibility (not Windows OS)";}
    private String networkInfo(){
        try{StringBuilder b=new StringBuilder("Network interfaces:\n");Enumeration<NetworkInterface> es=NetworkInterface.getNetworkInterfaces();
            while(es.hasMoreElements()){NetworkInterface n=es.nextElement();b.append(n.getName()).append(n.isUp()?" UP":" DOWN").append("\n");Enumeration<InetAddress> ips=n.getInetAddresses();while(ips.hasMoreElements())b.append("  ").append(ips.nextElement().getHostAddress()).append("\n");}return b.toString();
        }catch(Exception e){return "Network info unavailable: "+e.getMessage();}
    }
    private String help(){return "LOCAL WINDOWS-STYLE COMMANDS\n  dir / ls             List files\n  cd <path>            Change directory\n  mkdir <name>         Create directory\n  type <file>          Display text file\n  copy <src> <dst>     Copy file\n  move <src> <dst>     Move file\n  del <file>           Delete file\n  echo <text>          Print text\n  cls                  Clear output\n  ver, whoami, hostname, systeminfo, ipconfig, date, time\n\nANDROID SHELL MODE\n  Executes /system/bin/sh commands.\n\nNOTE: Local CMD/PowerShell modes emulate a limited command subset. They are not Microsoft cmd.exe or PowerShell.";
    }
}
