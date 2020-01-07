using System;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using Utils;

namespace ClientApp
{
    // Application that makes requests to our server 
    class Program
    {
        
        private const int Port = 8081;

        static async Task Main(string[] args)
        {

            var res = await SendMessage();
            
            Console.WriteLine($"response: {res}");

        }
        
        private static async Task<ServerObjects.Response> SendMessage()  
        {  
            try // Try connecting and send JSON
            {
                var serializer = new JsonSerializer();

                var client = new TcpClient();

                //Creates a TCPClient using a local end point.

                client.Connect(IPAddress.Loopback, Port);
                
                Console.WriteLine($"connected: {client.Connected}");
                
                var stream = client.GetStream();  
                
                var writer = new JsonTextWriter(new StreamWriter(stream));
                var req = new ServerObjects.Request
                {
                    Method = "CREATE",
                    Path = "test",
                    Headers = new JObject(),
                    Payload = new JObject()
                };
                    
                serializer.Serialize(writer, req);
                //writer.WriteRaw(JsonConvert.SerializeObject(req));
                    
                Console.WriteLine("================================");  
                Console.WriteLine("=   Connected to the server    =");  
                Console.WriteLine("================================");  
                Console.WriteLine("Waiting for response...");

                var reader = new JsonTextReader(new StreamReader(stream));

                while (true)
                {
                    do
                    {
                        await reader.ReadAsStringAsync();
                        Console.WriteLine($"advanced to {reader.TokenType}");
                    } while (reader.TokenType != JsonToken.StartObject
                             && reader.TokenType != JsonToken.None);

                
                    var json = await JObject.LoadAsync(reader);
                
                    var response = json.ToObject<ServerObjects.Response>();

                    return response;
                }
            }  
            catch (Exception e) // Catch exceptions  
            {  
                Console.WriteLine(e.Message);  
            }

            return null;
        }
    }
}