# SimpleChatProgram
The focus of this project was to connect multiple users to one server and how to send encrypted user messages to the server, and then other users recieve encrypted messages from the server. Messages are encrypted with RSA Algorithm.

### pda_TCPClientMT
This file is the client's script. It is made of 3 threads:
  * First thread controls the starting and stoping of the program
  * Next a thread to send Encrypted messages
  * Finally a thread to recieve and decrypt messages
  
### pda_TCPServerMT
This is the server script that was hosted on Google Cloud. It has a main thread and a thread for each client.
  * The main thread controls starting and stoping the program, writing messeages to a chat log file and sends messages to all other users
  * The client handler thread is responsable for encrypting/decrpting messages and sending/recieving messages
