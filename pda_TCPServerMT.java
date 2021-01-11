import java.io.*;
import java.net.*;
import java.util.*;

public class pda_TCPServerMT {
    // Programmer: Parker Allen
    // Server program
    // File name: pda_TCPServerMT.java

    private static ServerSocket servSock;
    private static int port = 20010;    //hard coded port
    static String hostName;

    static int n= 1823 , g = 1019;      //hard coded g and n

    //chat log file and file writer
    static File chatLog;
    static FileWriter chatLogWriter;

    //list of users in session
    static ArrayList<ClientHandler> users;

    public static void main(String[] args)
    {
        System.out.println("Opening port...\n");
        try{
            // Create a server object
            ReadArgs(args);
            servSock = new ServerSocket(port);
            hostName = InetAddress.getLocalHost().getHostName();
            users = new ArrayList<>();
        }
        catch(IOException e){
            System.out.println("Unable to attach to port!");
            System.exit(1);
        }
        do
        {
            run();
        }while (true);
    }

    //reads args
    private static void ReadArgs(String args[])
    {
        for(int i = 0; i < args.length - 1; i+=2) {
            //sets port
            if (args[i].equals("-u"))
                port = Integer.parseInt(args[i + 1]);
            //port
            else if (args[i].equals("-g"))
                g = Integer.parseInt(args[i + 1]);
            //host
            else if (args[i].equals("-n"))
                n = Integer.parseInt(args[i + 1]);
            //anything else
            else {
                System.out.println("Invalid Input\n" + "Try -u for username, -h for host, and -p for port");
                System.exit(1);
            }
        }
    }

    static void run()
    {
        Socket link = null;
        try
        {
            // Put the server into a waiting state
            link = servSock.accept();

            // Create a thread to handle this connection
            ClientHandler handler = new ClientHandler(link, g, n);

            //create chat file
            if(users.size() == 0)
            {
                chatLog = new File("chat.txt");
                chatLogWriter = new FileWriter(chatLog);
            }

            AddUser(handler);
            handler.start();
        }
        catch(IOException e){
            e.printStackTrace();
        }
    }

    //adds users synchronized
    static synchronized void AddUser(ClientHandler u)
    {
        users.add(u);
    }

    //send messages to all other users synchronized
    //writes message to chat file
    static synchronized void SendMessage(String message, ClientHandler user, boolean same) throws IOException
    {
        String mes = message;
        if(!same)
            mes = user.username + ": " + mes;
        //System.out.println(mes);

        chatLogWriter.write(mes + "\n");
        chatLogWriter.flush();

        for (ClientHandler ch : users)
        {
            if(!ch.equals(user))
                ch.WriteCypherMessage(mes);
            else if(same)
                ch.WriteCypherMessage(message);
        }
    }

    //removes users from users list synchronized
    //deletes chat file if 0 users in session
    static synchronized void RemoveUser(ClientHandler user)
    {
        users.remove(user);
        if(users.size() == 0)
        {
            try {
                chatLogWriter.close();
                chatLog.delete();
                System.out.println("!!! Waiting for the next connection... !!!\n\n");
            } catch (Exception e)
            {
                System.out.println("Unable to close file!");
                System.exit(1);
            }
        }
    }

    //converts time in milliseconds to h :: m :: s :: ms
    public static String TimeConverter(long t)
    {
        int h, m, s, ms;
        ms = (int)t;
        h = ms / 3600000;
        ms = ms % 3600000;
        m = ms / 60000;
        ms = ms % 60000;
        s = ms / 1000;
        ms = ms % 1000;
        return h + " :: " + m + " :: " + s + " :: " + ms;
    }
}
class ClientHandler extends Thread
{
    private Socket client;
    private BufferedInputStream in;
    private BufferedOutputStream out;
    String username;
    int key;

    public ClientHandler(Socket s, int g, int n)
    {
        // set up the socket
        client = s;
        try {
            // Set up input and output streams for socket
            in = new BufferedInputStream(client.getInputStream());
            out = new BufferedOutputStream(client.getOutputStream());

            //send g, n and g^x mod n
            int x = (int)(Math.random() * 100 + 100);
            WriteMessage(IntToByte(g));
            WriteMessage(IntToByte(n));
            WriteMessage(IntToByte(CalcKey(g, x, n)));

            //receive g^y mod n and calculate session key
            int b = Integer.parseInt(new String(ReadMessage()));
            int sessionKey = CalcKey(b, x, n);
            key = sessionKey & 0xff;

            //set username and print stuff
            username = new String(Cypher(ReadMessage()));
            System.out.println(username + "\ng = " + g + "\nn = " + n + "\nSession key = " + sessionKey + "\nbyte pad = " + Integer.toBinaryString(key) + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run()
    {
        long startTime = System.currentTimeMillis();
        int numMessages = 0;

        ReadChatLog();      //read older messages

        try{
            pda_TCPServerMT.SendMessage(username + " has joined", this, true);
            String message = "";

            //receive messages from client
            while(!(message = new String(Cypher(ReadMessage()))).contains("DONE") )
            {
                numMessages++;
                pda_TCPServerMT.SendMessage(message,this,false);
            }

            //gets users session time
            long estimatedTime = System.currentTimeMillis() - startTime;

            //send leaving messages and statistics
            pda_TCPServerMT.SendMessage(username + " has left", this, true);
            WriteCypherMessage("\nServer received " + numMessages + " messages\n");
            WriteCypherMessage(pda_TCPServerMT.TimeConverter(estimatedTime) + "");
            WriteCypherMessage("DONE");

        }catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                //make the client leave
                client.close();
                pda_TCPServerMT.RemoveUser(this);
            } catch (IOException e) {
                System.out.println("Unable to disconnect!" + e);
                System.exit(1);
            }
        }
    }

    //reads and outputs the chat log to client
    void ReadChatLog()
    {
        try{
            BufferedReader chatLogReader = new BufferedReader(new FileReader(pda_TCPServerMT.chatLog));
            while(chatLogReader.ready())
                WriteCypherMessage(chatLogReader.readLine());

            chatLogReader.close();
        } catch (Exception e){}
    }

    //convert ints to byte array
    byte[] IntToByte(int i)
    {
        return Integer.toString(i).getBytes();
    }

    //calculates g^x mod n
    int CalcKey(int g, int x, int n)
    {
        int rtn = 1;
        for(int i = 0; i < x; i++)
            rtn = (rtn * g) % n;
        return rtn;
    }

    //cypher the message then write it
    public void WriteCypherMessage(String m)
    {
        WriteMessage(Cypher(m.getBytes()));
    }

    //writes messages to client
    public void WriteMessage(byte[] message)
    {
        try {
            out.write(new Integer(message.length).byteValue());
            out.write(message);
            out.flush();
        }catch (Exception e){
            System.out.println(e);
        }
    }

    //reads and decyphers messages from client
    public byte[] ReadMessage()
    {
        try {
            byte[] read = new byte[in.read()];
            for(int i = 0; i < read.length; i++)
                read[i] = (byte)in.read();

            return read;
        } catch (Exception e){
            System.out.println(e);
        }
        return null;
    }

    //XOR given byte array
    public byte[] Cypher(byte[] bArr)
    {
        for(int i = 0; i < bArr.length; i++)
            bArr[i] = (byte)(bArr[i] ^ key);
        return bArr;
    }
}
