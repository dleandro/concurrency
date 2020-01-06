using System;
using System.Net;
using System.Net.Sockets;

namespace ClientApp
{
    // Application that makes requests to our server 
    class Program
    {
        
        private const int Port = 8081;

        static void Main(string[] args)
        {
        }
        
        private static byte[] sendMessage(byte[] messageBytes)  
        {  
            const int bytesize = 1024 * 1024;  
            try // Try connecting and send the message bytes  
            {  
                //Creates a TCPClient using a local end point.
                var ipAddress = Dns.GetHostEntry (Dns.GetHostName ()).AddressList[0];
                var ipLocalEndPoint = new IPEndPoint(ipAddress, Port);
                var client = new TcpClient(ipLocalEndPoint); // Create a new connection  
               
                var stream = client.GetStream();  
  
                stream.Write(messageBytes, 0, messageBytes.Length); // Write the bytes  
                Console.WriteLine("================================");  
                Console.WriteLine("=   Connected to the server    =");  
                Console.WriteLine("================================");  
                Console.WriteLine("Waiting for response...");  
  
                messageBytes = new byte[bytesize]; // Clear the message   
  
                // Receive the stream of bytes  
                stream.Read(messageBytes, 0, messageBytes.Length);  
  
                // Clean up  
                stream.Dispose();  
                client.Close();  
            }  
            catch (Exception e) // Catch exceptions  
            {  
                Console.WriteLine(e.Message);  
            }  
  
            return messageBytes; // Return response  
        }  
        
    }
}