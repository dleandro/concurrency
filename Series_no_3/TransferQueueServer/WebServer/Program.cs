using System;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

namespace WebServer
{
    class Program
    {
        
        // Represents a request
        public class Request
        {
            public string Method { get; set; }
            
            public string Path { get; set; }
            public JObject Headers { get; set; }
            public JObject Payload { get; set; }

            public override string ToString()
            {
                return $"Method: {Method}, Path: {Path}, Headers: {Headers}, Payload: {Payload}";
            }
        }
        
        // represents a response
        public class Response
        {
            public int Status { get; set; }
            public JObject Headers { get; set; }
            public JObject Payload { get; set; }
        }
        
        private const int port = 8081;
        private static int counter;
        
        static async Task Main(string[] args)
        {
            var listener = new TcpListener(IPAddress.Loopback, port);
            var terminator = new Terminator();
            var cts = new CancellationTokenSource();
            var ct = cts.Token;
            Console.CancelKeyPress += (obj, eargs) =>
            {
                Log("CancelKeyPress, stopping server");
                cts.Cancel();
                listener.Stop();
                eargs.Cancel = true;
            };
            listener.Start();
            Log($"Listening on {port}");
            using (ct.Register(() => { listener.Stop(); }))
            {
                try
                {
                    while (!ct.IsCancellationRequested)
                    {
                        Log("Accepting new TCP client");
                        var client = await listener.AcceptTcpClientAsync();
                        var id = counter++;
                        Log($"connection accepted with id '{id}'");
                        Handle(id, client, ct, terminator);
                    }
                }
                catch (Exception e)
                {
                    // await AcceptTcpClientAsync will end up with an exception
                    Log($"Exception '{e.Message}' received");
                }

                Log("waiting shutdown");
                await terminator.Shutdown();
            }
        }

        private static void Log(string s)
        {
            Console.Out.WriteLine("[{0,2}|{1,8}|{2:hh:mm:ss.fff}]{3}",
                Thread.CurrentThread.ManagedThreadId,
                Thread.CurrentThread.IsThreadPoolThread ? "pool" : "non-pool", DateTime.Now,
                s);
        }
    }
}