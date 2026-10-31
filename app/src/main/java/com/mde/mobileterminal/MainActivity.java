package com.mde.mobileterminal;

import android.app.*;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.content.*;
import android.net.Uri;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.text.InputType;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import com.jcraft.jsch.*;

public class MainActivity extends Activity {
    private static final int BG=Color.rgb(8,13,27), SURFACE=Color.rgb(17,25,44), SURFACE2=Color.rgb(23,34,58);
    private static final int TEXT=Color.rgb(238,243,255), MUTED=Color.rgb(143,158,185), CYAN=Color.rgb(39,220,231), PURPLE=Color.rgb(155,91,255), BLUE=Color.rgb(53,145,255);
    private LinearLayout root, content, terminalLog, bottomNav;
    private TextView status, pathLabel, terminalText;
    private final StringBuilder terminalBuffer=new StringBuilder();
    private EditText commandInput;
    private ScrollView terminalScroll;
    private File cwd, knownHosts;
    private String mode="CMD", activeTab="Terminal";
    private final ArrayList<String> history=new ArrayList<>();
    private int historyIndex=0;
    private EditText hostField,portField,userField,passwordField,knownHostField;
    private boolean remoteReady=false;
    private String remoteHost="",remoteUser="",remotePassword="";
    private int remotePort=22;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        cwd=getExternalFilesDir(null); if(cwd==null) cwd=getFilesDir();
        knownHosts=new File(getFilesDir(),"known_hosts");
        buildShell();
        showTab("Terminal");
        appendTerminal("MDE Mobile Terminal  •  v1.1\nLocal Android workspace ready.\nCMD and PowerShell modes provide local compatibility commands; Android mode runs the device shell.\nType help to get started.\n\n");
    }

    private GradientDrawable shape(int color,int stroke,int radius) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius));
        if(stroke!=0)d.setStroke(dp(1),stroke); return d;
    }
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private TextView label(String s,int size,int color,boolean bold) {
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        return t;
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private void pad(View v,int a,int b,int c,int d){v.setPadding(dp(a),dp(b),dp(c),dp(d));}
    private TextView action(String s,Runnable r) {
        TextView t=label(s,13,CYAN,true);t.setGravity(Gravity.CENTER);t.setBackground(shape(SURFACE2,Color.rgb(39,62,95),12));pad(t,14,10,14,10);
        t.setOnClickListener(v->r.run());return t;
    }
    private void buildShell(){
        root=column();root.setBackgroundColor(BG);pad(root,16,10,16,8);setContentView(root);
        LinearLayout header=row();
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.ic_mde_logo);header.addView(logo,lp(42,42));
        LinearLayout brand=column();pad(brand,10,0,0,0);
        brand.addView(label("MDE",19,TEXT,true));brand.addView(label("MOBILE TERMINAL",9,CYAN,true));
        header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView search=action("⌕",()->showCommandPalette());header.addView(search,lp(44,40));
        TextView more=action("⋮",()->showMore());LinearLayout.LayoutParams mp=lp(40,40);mp.leftMargin=dp(8);header.addView(more,mp);
        root.addView(header,lp(-1,48));
        status=label("●  LOCAL DEVICE  ·  READY",11,CYAN,true);pad(status,3,12,3,10);root.addView(status);
        content=column();root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        bottomNav=row();bottomNav.setBackground(shape(SURFACE,Color.rgb(34,49,77),18));pad(bottomNav,4,5,4,5);
        root.addView(bottomNav,lp(-1,62));
        buildNav();
    }
    private void buildNav(){
        bottomNav.removeAllViews();
        String[] names={"Terminal","Files","Tools","Connections","Settings"};String[] icons={"⌘","▣","⌘","⌁","⚙"};
        for(int i=0;i<names.length;i++){
            final String n=names[i];LinearLayout item=column();item.setGravity(Gravity.CENTER);
            int c=activeTab.equals(n)?CYAN:MUTED;
            item.addView(label(icons[i],19,c,true));item.addView(label(n,9,c,activeTab.equals(n)));
            item.setOnClickListener(v->showTab(n));bottomNav.addView(item,new LinearLayout.LayoutParams(0,-1,1));
        }
    }
    private void card(LinearLayout parent,View child){
        LinearLayout wrap=column();pad(wrap,13,12,13,12);wrap.setBackground(shape(SURFACE,Color.rgb(32,48,76),16));wrap.addView(child);parent.addView(wrap,lp(-1,-2));
    }
    private void section(String title,String subtitle){
        TextView t=label(title,17,TEXT,true);content.addView(t);if(subtitle!=null){TextView s=label(subtitle,11,MUTED,false);pad(s,0,3,0,12);content.addView(s);}
    }
    private void showTab(String tab){
        activeTab=tab;content.removeAllViews();buildNav();
        if(tab.equals("Terminal"))showTerminal();else if(tab.equals("Files"))showFiles();else if(tab.equals("Tools"))showTools();else if(tab.equals("Connections"))showConnections();else showSettings();
    }
    private void showTerminal(){
        section("Terminal","Local workspace  ·  "+cwd.getAbsolutePath());
        LinearLayout modes=row();String[] ms={"CMD","PowerShell","Android"};
        for(String m:ms){TextView b=action(m,()->{mode=m;showTab("Terminal");});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(38),1);p.rightMargin=dp(6);modes.addView(b,p);}
        content.addView(modes,lp(-1,42));
        LinearLayout panel=column();panel.setBackground(shape(Color.rgb(3,7,16),Color.rgb(29,51,79),16));pad(panel,12,12,12,10);
        terminalScroll=new ScrollView(this);terminalScroll.setFillViewport(true);
        terminalText=label(terminalBuffer.toString(),12,Color.rgb(207,225,246),false);terminalText.setTypeface(android.graphics.Typeface.MONOSPACE);terminalText.setTextIsSelectable(true);terminalText.setLineSpacing(dp(3),1f);
        terminalScroll.addView(terminalText);panel.addView(terminalScroll,new LinearLayout.LayoutParams(-1,0,1));
        content.addView(panel,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout inputRow=row();inputRow.setPadding(0,dp(10),0,dp(6));
        commandInput=new EditText(this);commandInput.setSingleLine(true);commandInput.setTextSize(13);commandInput.setTextColor(TEXT);commandInput.setHintTextColor(MUTED);commandInput.setHint("Enter a command…");commandInput.setTypeface(android.graphics.Typeface.MONOSPACE);
        commandInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);commandInput.setBackground(shape(SURFACE2,Color.rgb(43,65,99),12));pad(commandInput,12,0,12,0);
        commandInput.setOnEditorActionListener((v,a,e)->{runCommand();return true;});inputRow.addView(commandInput,new LinearLayout.LayoutParams(0,dp(48),1));
        TextView go=action("➤",this::runCommand);LinearLayout.LayoutParams gp=lp(54,48);gp.leftMargin=dp(8);inputRow.addView(go,gp);content.addView(inputRow);
        LinearLayout quick=row();String[] cmds=mode.equals("Android")?new String[]{"pwd","ls","cd","whoami"}:new String[]{"help","dir","cd","systeminfo"};
        for(String c:cmds){TextView q=action(c,()->{commandInput.setText(c);runCommand();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(36),1);p.rightMargin=dp(5);quick.addView(q,p);}content.addView(quick,lp(-1,40));
        TextView clear=label("Clear output     ·     History: "+history.size(),10,MUTED,false);clear.setGravity(Gravity.END);clear.setOnClickListener(v->clearTerminal());content.addView(clear);
    }
    private void appendTerminal(String s){
        terminalBuffer.append(s);
        if(terminalText!=null){terminalText.setText(terminalBuffer.toString());if(terminalScroll!=null)terminalScroll.post(()->terminalScroll.fullScroll(View.FOCUS_DOWN));}
    }
    private void clearTerminal(){
        terminalBuffer.setLength(0);
        if(terminalText!=null)terminalText.setText("");
    }
    private void runCommand(){
        if(commandInput==null)return;String cmd=commandInput.getText().toString().trim();if(cmd.isEmpty())return;
        commandInput.setText("");history.add(cmd);historyIndex=history.size();
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(commandInput.getWindowToken(),0);
        String prompt=mode.equals("Android")?"$ ":mode.equals("PowerShell")?"PS MDE> ":"MDE> ";
        appendTerminal(prompt+cmd+"\n");
        final String selected=mode;
        new Thread(()->{
            String result;
            if(remoteReady) result=runRemote(cmd);
            else if(selected.equals("Android"))result=runAndroidShell(cmd);
            else result=runCompat(cmd,selected);
            final String out=result;
            runOnUiThread(()->appendTerminal(out+(out.endsWith("\n")?"":"\n")));
        }).start();
    }
    private String runAndroidShell(String cmd){
        java.lang.Process p=null;
        try{
            p=new ProcessBuilder("/system/bin/sh","-c",cmd).directory(cwd).redirectErrorStream(true).start();
            ByteArrayOutputStream out=new ByteArrayOutputStream();InputStream in=p.getInputStream();byte[] b=new byte[4096];long end=System.currentTimeMillis()+15000;
            while(System.currentTimeMillis()<end){while(in.available()>0){int n=in.read(b);if(n<0)break;out.write(b,0,n);}try{if(p.exitValue()>=0)break;}catch(IllegalThreadStateException ignored){}Thread.sleep(40);}
            try{p.exitValue();}catch(IllegalThreadStateException e){p.destroyForcibly();return out.toString("UTF-8")+"\n[Command timed out after 15 seconds]";}
            return out.toString("UTF-8")+"\n[exit "+p.exitValue()+"]";
        }catch(Exception e){if(p!=null)p.destroyForcibly();return "Shell error: "+e.getMessage();}
    }
    private String runCompat(String raw,String selected){
        String c=raw.trim(),l=c.toLowerCase(Locale.ROOT);
        if(l.equals("help")||l.equals("get-help"))return helpText();
        if(l.equals("cls")||l.equals("clear")){runOnUiThread(this::clearTerminal);return "";}
        if(l.equals("pwd")||l.equals("cd"))return cwd.getAbsolutePath();
        if(l.equals("dir")||l.equals("ls")||l.equals("get-childitem")){
            File[] fs=cwd.listFiles();if(fs==null)return "Directory unavailable.";
            StringBuilder b=new StringBuilder("Directory of "+cwd+"\n");for(File f:fs)b.append(f.isDirectory()?"[DIR]  ":"       ").append(f.getName()).append(f.isFile()?"  ("+f.length()+" B)":"").append("\n");return b.toString();
        }
        if(l.startsWith("cd ")){File f=resolve(c.substring(3).trim());if(f.isDirectory()){cwd=f;return "Directory changed: "+cwd;}return "Path not found.";}
        if(l.startsWith("mkdir ")||l.startsWith("md ")){File f=resolve(c.substring(c.indexOf(' ')+1).trim());return f.mkdirs()?"Directory created.":"Could not create directory.";}
        if(l.startsWith("echo "))return c.substring(5);
        if(l.startsWith("type ")||l.startsWith("cat ")){try{return readFile(resolve(c.substring(c.indexOf(' ')+1).trim()));}catch(Exception e){return "Read failed: "+e.getMessage();}}
        if(l.startsWith("del ")||l.startsWith("erase ")||l.startsWith("rm ")){File f=resolve(c.substring(c.indexOf(' ')+1).trim());return f.delete()?"Deleted "+f.getName():"Delete failed. Check permissions and path.";}
        if(l.startsWith("touch ")){try{File f=resolve(c.substring(6).trim());if(!f.exists()&&!f.createNewFile())return "Could not create file.";return "File ready: "+f.getName();}catch(Exception e){return e.getMessage();}}
        if(l.startsWith("copy ")||l.startsWith("cp ")||l.startsWith("move ")){
            String[] a=c.substring(c.indexOf(' ')+1).split("\\s+",2);if(a.length!=2)return "Usage: copy <source> <destination>";
            File src=resolve(a[0]),dst=resolve(a[1]);try{if(l.startsWith("move "))return src.renameTo(dst)?"Moved.":"Move failed.";
                try(InputStream in=new FileInputStream(src);OutputStream out=new FileOutputStream(dst)){byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)out.write(buf,0,n);}return "Copied "+src.length()+" bytes.";
            }catch(Exception e){return "File operation failed: "+e.getMessage();}
        }
        if(l.equals("ver"))return "MDE local compatibility environment";
        if(l.equals("whoami"))return "android:mde";
        if(l.equals("hostname"))return android.os.Build.DEVICE;
        if(l.equals("systeminfo")||l.equals("get-computerinfo"))return deviceInfo();
        if(l.equals("ipconfig")||l.equals("ifconfig"))return networkInfo();
        if(l.equals("date"))return new Date().toString();
        if(l.equals("time"))return new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date());
        return "Command not supported in local "+selected+" compatibility mode.\nThis is not the Microsoft CMD/PowerShell runtime. Use Android mode for native device shell commands.";
    }
    private File resolve(String path){String p=path.trim();if((p.startsWith("\"")&&p.endsWith("\""))||(p.startsWith("'")&&p.endsWith("'")))p=p.substring(1,p.length()-1);File f=new File(p);return f.isAbsolute()?f:new File(cwd,p);}
    private String readFile(File f)throws Exception{if(!f.isFile())return "File not found.";if(f.length()>1024*1024)return "File exceeds 1 MB output limit.";return new String(java.nio.file.Files.readAllBytes(f.toPath()),"UTF-8");}
    private String deviceInfo(){return "Device: "+android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL+"\nAndroid: "+android.os.Build.VERSION.RELEASE+" (API "+android.os.Build.VERSION.SDK_INT+")\nABI: "+Arrays.toString(android.os.Build.SUPPORTED_ABIS)+"\nWorkspace: "+cwd.getAbsolutePath();}
    private String networkInfo(){try{StringBuilder b=new StringBuilder("Network interfaces:\n");Enumeration<NetworkInterface> e=NetworkInterface.getNetworkInterfaces();while(e.hasMoreElements()){NetworkInterface n=e.nextElement();b.append(n.getName()).append(n.isUp()?" UP":" DOWN").append("\n");Enumeration<InetAddress> a=n.getInetAddresses();while(a.hasMoreElements())b.append("  ").append(a.nextElement().getHostAddress()).append("\n");}return b.toString();}catch(Exception e){return "Network info unavailable: "+e.getMessage();}}
    private String helpText(){return "LOCAL COMMANDS\nhelp, cls, pwd, dir/ls, cd <path>, mkdir <name>, touch <file>, type <file>, copy <src> <dst>, move <src> <dst>, del <file>, echo <text>, date, time, hostname, whoami, systeminfo, ipconfig\n\nAndroid mode runs /system/bin/sh commands. CMD and PowerShell are compatibility subsets, not Microsoft runtimes.";}

    private void showFiles(){
        section("Files","App workspace  ·  "+cwd.getAbsolutePath());
        LinearLayout tools=row();tools.addView(action("＋ New folder",()->createFile(true)),new LinearLayout.LayoutParams(0,dp(42),1));
        TextView nf=action("＋ New file",()->createFile(false));LinearLayout.LayoutParams nfp=new LinearLayout.LayoutParams(0,dp(42),1);nfp.leftMargin=dp(8);tools.addView(nf,nfp);content.addView(tools);
        TextView up=action("↑ Parent directory",()->{if(cwd.getParentFile()!=null&&cwd.getParentFile().canRead()){cwd=cwd.getParentFile();showTab("Files");}});LinearLayout.LayoutParams upp=lp(-1,40);upp.topMargin=dp(8);content.addView(up,upp);
        ScrollView scroll=new ScrollView(this);LinearLayout list=column();File[] files=cwd.listFiles();if(files==null||files.length==0)list.addView(label("This folder is empty.",13,MUTED,false));
        else{Arrays.sort(files,(a,b)->{if(a.isDirectory()!=b.isDirectory())return a.isDirectory()?-1:1;return a.getName().compareToIgnoreCase(b.getName());});
            for(File f:files){LinearLayout item=row();TextView name=label((f.isDirectory()?"▰  ":"▤  ")+f.getName(),13,TEXT,f.isDirectory());item.addView(name,new LinearLayout.LayoutParams(0,dp(46),1));
                TextView size=label(f.isDirectory()?"FOLDER":formatSize(f.length()),10,MUTED,false);item.addView(size);
                item.setOnClickListener(v->{if(f.isDirectory()){cwd=f;showTab("Files");}else showFileActions(f);});pad(item,10,0,10,0);item.setBackground(shape(SURFACE,0,10));LinearLayout.LayoutParams ip=lp(-1,48);ip.topMargin=dp(5);list.addView(item,ip);}
        }
        scroll.addView(list);content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    }
    private String formatSize(long n){if(n<1024)return n+" B";if(n<1048576)return String.format(Locale.US,"%.1f KB",n/1024f);return String.format(Locale.US,"%.1f MB",n/1048576f);}
    private void createFile(boolean dir){
        EditText e=new EditText(this);e.setSingleLine(true);e.setHint(dir?"Folder name":"File name");
        new AlertDialog.Builder(this).setTitle(dir?"Create folder":"Create file").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Create",(d,w)->{String n=e.getText().toString().trim();if(n.isEmpty())return;File f=new File(cwd,n);try{boolean ok=dir?f.mkdirs():f.createNewFile();toast(ok?"Created "+n:"Could not create item");showTab("Files");}catch(Exception ex){toast(ex.getMessage());}}).show();
    }
    private void showFileActions(File f){
        String[] opts={"Open / view","Rename","Delete","Cancel"};new AlertDialog.Builder(this).setTitle(f.getName()).setItems(opts,(d,w)->{
            if(w==0){try{new AlertDialog.Builder(this).setTitle(f.getName()).setMessage(readFile(f)).setPositiveButton("Close",null).show();}catch(Exception e){toast(e.getMessage());}}
            if(w==1){EditText e=new EditText(this);e.setText(f.getName());new AlertDialog.Builder(this).setTitle("Rename").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Save",(x,y)->{File dest=new File(f.getParentFile(),e.getText().toString().trim());toast(f.renameTo(dest)?"Renamed":"Rename failed");showTab("Files");}).show();}
            if(w==2)new AlertDialog.Builder(this).setTitle("Delete "+f.getName()+"?").setMessage("This cannot be undone.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(x,y)->{toast(f.delete()?"Deleted":"Delete failed");showTab("Files");}).show();
        }).show();
    }
    private void showTools(){
        section("Developer tools","Quick access to local device utilities");
        String[][] groups={{"System information","systeminfo"},{"Network interfaces","ipconfig"},{"Current directory","pwd"},{"List workspace","dir"},{"Date and time","date"},{"Terminal help","help"}};
        for(String[] g:groups){LinearLayout item=row();LinearLayout tx=column();tx.addView(label(g[0],14,TEXT,true));tx.addView(label(g[1],11,MUTED,false));item.addView(tx,new LinearLayout.LayoutParams(0,-2,1));item.addView(label("›",24,CYAN,true));item.setOnClickListener(v->{mode="CMD";showTab("Terminal");commandInput.setText(g[1]);runCommand();});LinearLayout.LayoutParams p=lp(-1,62);p.topMargin=dp(7);content.addView(item,p);item.setBackground(shape(SURFACE,Color.rgb(31,49,77),14));pad(item,14,8,14,8);}
    }
    private void showConnections(){
        section("Connections","Secure SSH connection to your own computer");
        LinearLayout form=column();hostField=field("Host name or IP");portField=field("Port (22)");portField.setText("22");userField=field("Username");passwordField=field("Password");passwordField.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        knownHostField=field("Known_hosts entry (required)");knownHostField.setMinLines(2);knownHostField.setSingleLine(false);
        form.addView(hostField);form.addView(portField);form.addView(userField);form.addView(passwordField);form.addView(knownHostField);
        TextView note=label("Host key verification is mandatory. Paste the matching line from a trusted known_hosts file; unknown hosts are rejected. Password is held in memory only.",11,MUTED,false);pad(note,0,8,0,10);form.addView(note);
        TextView connect=action("Connect using SSH",()->connectSsh());LinearLayout.LayoutParams cp=lp(-1,48);cp.topMargin=dp(6);form.addView(connect,cp);
        TextView disconnect=action("Disconnect",()->{remoteReady=false;remotePassword="";status.setText("●  LOCAL DEVICE  ·  READY");toast("Disconnected");});LinearLayout.LayoutParams dp=lp(-1,44);dp.topMargin=dp(8);form.addView(disconnect,dp);
        ScrollView sc=new ScrollView(this);sc.addView(form);content.addView(sc);
    }
    private EditText field(String hint){EditText e=new EditText(this);e.setHint(hint);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setTextSize(13);e.setBackground(shape(SURFACE2,Color.rgb(39,57,88),12));pad(e,12,0,12,0);LinearLayout.LayoutParams p=lp(-1,48);p.topMargin=dp(7);e.setLayoutParams(p);return e;}
    private void connectSsh(){
        String host=hostField.getText().toString().trim(),user=userField.getText().toString().trim(),pass=passwordField.getText().toString();
        String kh=knownHostField.getText().toString().trim();int port;try{port=Integer.parseInt(portField.getText().toString().trim());}catch(Exception e){toast("Invalid port");return;}
        if(host.isEmpty()||user.isEmpty()||pass.isEmpty()||kh.isEmpty()){toast("Complete all fields and provide a trusted known_hosts entry");return;}
        try{FileOutputStream f=new FileOutputStream(knownHosts,false);f.write(kh.getBytes("UTF-8"));f.write('\n');f.close();}catch(Exception e){toast("Could not save host key: "+e.getMessage());return;}
        status.setText("●  CONNECTING  ·  "+host);
        new Thread(()->{Session s=null;try{JSch j=new JSch();j.setKnownHosts(knownHosts.getAbsolutePath());s=j.getSession(user,host,port);s.setPassword(pass);Properties p=new Properties();p.put("StrictHostKeyChecking","yes");s.setConfig(p);s.connect(12000);s.disconnect();
            runOnUiThread(()->{remoteReady=true;remoteHost=host;remoteUser=user;remotePassword=pass;remotePort=port;status.setText("●  SSH CONNECTED  ·  "+host);toast("SSH host key verified; connected");});
        }catch(Exception e){String msg=e.getMessage();runOnUiThread(()->{remoteReady=false;status.setText("●  LOCAL DEVICE  ·  READY");toast("SSH failed: "+msg);});}}).start();
    }
    private String runRemote(String command){
        Session s=null;ChannelExec ch=null;try{JSch j=new JSch();j.setKnownHosts(knownHosts.getAbsolutePath());s=j.getSession(remoteUser,remoteHost,remotePort);s.setPassword(remotePassword);Properties p=new Properties();p.put("StrictHostKeyChecking","yes");s.setConfig(p);s.connect(10000);
            ch=(ChannelExec)s.openChannel("exec");String escaped=command.replace("'","''");ch.setCommand("powershell.exe -NoLogo -NoProfile -NonInteractive -Command '"+escaped+"'");ch.setInputStream(null);ByteArrayOutputStream out=new ByteArrayOutputStream();ch.setOutputStream(out);ch.setErrStream(out);ch.connect(8000);
            long end=System.currentTimeMillis()+20000;while(!ch.isClosed()&&System.currentTimeMillis()<end)Thread.sleep(80);if(!ch.isClosed())return "Remote command timed out.";return out.toString("UTF-8");
        }catch(Exception e){return "SSH command failed: "+e.getMessage();}finally{if(ch!=null)ch.disconnect();if(s!=null)s.disconnect();}
    }
    private void showSettings(){
        section("Settings","Personalize your terminal");
        setting("Appearance","Dark navy · electric blue · violet");
        setting("Terminal behaviour","Command history · 15-second local timeout");
        setting("Workspace","App-private storage · "+cwd.getAbsolutePath());
        setting("Security","SSH host key verification is required");
        setting("About","MDE Mobile Terminal · Version 1.1");
        TextView reset=action("Reset terminal output",()->{clearTerminal();showTab("Terminal");});LinearLayout.LayoutParams p=lp(-1,44);p.topMargin=dp(12);content.addView(reset,p);
    }
    private void setting(String title,String sub){LinearLayout item=column();item.addView(label(title,14,TEXT,true));TextView t=label(sub,11,MUTED,false);pad(t,0,4,0,0);item.addView(t);pad(item,13,11,13,11);item.setBackground(shape(SURFACE,Color.rgb(30,47,75),14));LinearLayout.LayoutParams p=lp(-1,-2);p.topMargin=dp(8);content.addView(item,p);}
    private void showCommandPalette(){
        EditText e=new EditText(this);e.setHint("Search commands");String[] items={"help","dir","pwd","ls","cd","mkdir","touch","systeminfo","ipconfig","date","time","whoami","hostname","cls"};
        new AlertDialog.Builder(this).setTitle("Command palette").setView(e).setItems(items,(d,w)->{showTab("Terminal");commandInput.setText(items[w]);runCommand();}).setNegativeButton("Close",null).show();
    }
    private void showMore(){new AlertDialog.Builder(this).setTitle("MDE Mobile Terminal").setItems(new String[]{"Command palette","Help","About"},(d,w)->{if(w==0)showCommandPalette();else if(w==1)new AlertDialog.Builder(this).setTitle("Help").setMessage(helpText()).setPositiveButton("OK",null).show();else showTab("Settings");}).show();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override public void onBackPressed(){if(!activeTab.equals("Terminal"))showTab("Terminal");else super.onBackPressed();}
}
