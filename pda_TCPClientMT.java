import javax.swing.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class pda_TCPClientMT extends JFrame {
    // Programmer: Parker Allen
    // Client program
    // File name: pda_TCPClientMT.java

    static InetAddress host;
    static int port = 20010;        //hard coded port
    static String username = "";
    static int key;

    //streams to send and recieve with server
    static BufferedInputStream in;
    static BufferedOutputStream out;

    static boolean done;    //boolean to close connection

    //ui
    static JFrame jf;
    static JTextArea jt;

    public static void main(String[] args)
    {
        try {
            // Get server IP-address
            host = InetAddress.getLocalHost();

            //String[] test = new String[]{"-u", "pal"};
            ReadArgs(args);
        }
        catch(UnknownHostException e){
            System.out.println("Host ID not found!");
            System.exit(1);
        }
        run();
    }

    //read args
    private static void ReadArgs(String args[]) throws UnknownHostException {
        for (int i = 0; i < args.length - 1; i+=2)
        {
            //username
            if (args[i].equals("-u"))
                username = args[i + 1];
                //port
            else if (args[i].equals("-p"))
                port = Integer.parseInt(args[i + 1]);
                //host
            else if (args[i].equals("-h"))
                host = InetAddress.getByName(args[i + 1]);
                //anything else
            else {
                System.out.println("Invalid Input\n" + "Try -u for username, -h for host, and -p for port");
                System.exit(1);
            }
        }

        //prompt for username
        if(username.equals(""))
        {
            System.out.println("Enter Username:");
            Scanner sc = new Scanner(System.in);
            username = sc.nextLine();
        }
    }

    static void run()
    {
        Socket link = null;
        try{
            // Establish a connection to the server
            link = new Socket(host,port);

            //setting up the ugly gui
            jf = new JFrame("Chat Log " + username);
            jt = new JTextArea();
            jt.setEditable(false);
            JPanel p = new JPanel();
            p.add(jt);
            JScrollPane pane = new JScrollPane(p);
            jf.add(pane);
            jf.setSize(300,400);
            jf.setVisible(true);
            jf.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

            //setup streams
            in = new BufferedInputStream(link.getInputStream());
            out = new BufferedOutputStream(link.getOutputStream());

            SetupKey();

            //send username
            WriteMessage(Cypher(username.getBytes()));

            //start receiving messages
            ReceiveMessenger receiveMessenger = new ReceiveMessenger();
            receiveMessenger.start();

            //start sending messages
            SendMessenger sendMessenger = new SendMessenger();
            sendMessenger.start();

            while(!done){}      //run
        }
        catch(IOException e){
            e.printStackTrace();
        }
        finally{
            try{
                //close connection
                System.out.println("\n!!!!! Closing connection... !!!!!");
                link.close();
            } catch(IOException e){
                System.out.println("Unable to disconnect!");
                System.exit(1);
            }
        }
        //sSystem.exit(1);
        System.out.println("Exit out of the gui to end program");
    }

    //setup the key
    static void SetupKey()
    {
        //reads g, n, g^y mod n
        int x = (int)(Math.random() * 100 + 100);
        int g = ByteToInt(ReadMessage());
        int n = ByteToInt(ReadMessage());
        int b = ByteToInt(ReadMessage());

        //sends g^x md n and calculates the key
        WriteMessage(Integer.toString(CalcKey(g,x,n)).getBytes());
        int sessionKey = CalcKey(b, x, n);
        key = sessionKey & 0xff;

        System.out.println(username + "\ng = " + g + "\nn = " + n + "\nSession key = " + sessionKey + "\nbyte pad = " + Integer.toBinaryString(key) + "\n");
    }

    //convert byte[] to int
    static int ByteToInt(byte[] bArr)
    {
        return Integer.parseInt(new String(bArr));
    }

    //calculates g^x mod n
    static int CalcKey(int g, int x, int n)
    {
        int rtn = 1;
        for(int i = 0; i < x; i++)
            rtn = (rtn * g) % n;
        return rtn;
    }

    //write byte[] to server
    public static void WriteMessage(byte[] message)
    {
        try {
            out.write(new Integer(message.length).byteValue());
            out.write(message);
            out.flush();
        }catch (Exception e){
            System.out.println(e);
        }
    }

    //read byte array from server
    public static byte[] ReadMessage()
    {
        try {
            byte[] read = new byte[in.read()];
            for(int i = 0; i < read.length; i++)
                read[i] = (byte)in.read();

            return read;
        } catch (IOException e){
            System.out.println(e);
        }
        return null;
    }

    //XOR a byte array by the key
    public static byte[] Cypher(byte[] bArr)
    {
        for(int i = 0; i < bArr.length; i++)
            bArr[i] = (byte)(bArr[i] ^ key);
        return bArr;
    }

    //writes to gui text area
    public static void WriteToChat(String s)
    {
        if(s.contains("DONE"))
        {
            done = true;
            return;
        }
        jt.append(s + "\n");
    }
}

class SendMessenger extends Thread
{
    public void run()
    {
        BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
        String message = "";
        try{
            do{
                //get input from user
                System.out.print("\bEnter message: ");
                message = userInput.readLine();

                //write message to chat
                if(!message.equals("DONE"))
                    pda_TCPClientMT.WriteToChat(message);

                //write message to server
                pda_TCPClientMT.WriteMessage(pda_TCPClientMT.Cypher(message.getBytes()));
            } while (!message.equals("DONE"));
        } catch (Exception e){
            System.out.println(e);
        }
    }
}

class ReceiveMessenger extends Thread
{
    public void run()
    {
        while(!pda_TCPClientMT.done)
            //read from server and write it to chat
            pda_TCPClientMT.WriteToChat(new String(pda_TCPClientMT.Cypher(pda_TCPClientMT.ReadMessage())));
    }
}

