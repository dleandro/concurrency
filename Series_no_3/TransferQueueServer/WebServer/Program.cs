using System;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using Utils;

namespace WebServer
{
    class Program
    {
        
        private const int Port = 8081;
        private static int _counter;
        private static readonly Router R =  new Router();
        
        // Starts a Semaphore that lets 5 threads enter our server,
        // the 6th thread will have to wait for a release
        private static readonly Semaphore ConnectionsCounter = new Semaphore(5, 5);
        public static bool Shutdown = false;
        
        static async Task Main(string[] args)
        {
            var listener = new TcpListener(IPAddress.Loopback, Port);
            var terminator = new Terminator();
            var cts = new CancellationTokenSource();
            var ct = cts.Token;
            R.InitializeRouterMethods();
            Console.CancelKeyPress += (obj, eargs) =>
            {
                Log("CancelKeyPress, stopping server");
                cts.Cancel();
                listener.Stop();
                eargs.Cancel = true;
            };
            listener.Start();
            Log($"Listening on {Port}");
            using (ct.Register(() => listener.Stop()))
            {
                try
                {
                    while (!ct.IsCancellationRequested)
                    {
                        Log("Accepting new TCP client");
                        var client = await listener.AcceptTcpClientAsync();

                        // check if we can acquire a unit for the new client
                        await Task.FromResult(ConnectionsCounter.WaitOne(TimeSpan.FromSeconds(4)))
                            .ContinueWith(task =>
                            {
                                // means that unit has been acquired
                                if (task.Result)
                                {
                                    var id = _counter++;
                                    Log($"connection accepted with id '{id}'");
                        
                                    Handle(id, client, ct, cts, terminator);

                                    // Release units before exiting the server
                                    ConnectionsCounter.Release();
                                }
                                else
                                {
                                    Log("maximum number of connections has been reached");
                                }

                            }, ct);
                    }
                }
                catch (Exception e)
                {
                    // await AcceptTcpClientAsync will end up with an exception
                    Log($"Exception '{e.Message}' received");
                }
                
                Log("waiting shutdown");
                await terminator.Shutdown().ContinueWith(_ => Shutdown = true, ct);
            }
        }
        
        private static readonly JsonSerializer serializer = new JsonSerializer();

        private static async void Handle(
            int id, TcpClient client, CancellationToken ct, CancellationTokenSource cts, Terminator terminator)
        {
            using (terminator.Enter()) 
            {
                try
                {
                    using (client)
                    {
                        var stream = client.GetStream();
                        var reader = new JsonTextReader(new StreamReader(stream))
                        {
                            // To support reading multiple top-level objects
                            SupportMultipleContent = true
                        };
                        var writer = new JsonTextWriter(new StreamWriter(stream));
                        while (true)
                        {
                            try
                            {
                                // to consume any bytes until start of object ('{')
                                do
                                {
                                    await reader.ReadAsync(ct);
                                    Log($"advanced to {reader.TokenType}");
                                } while (reader.TokenType != JsonToken.StartObject
                                         && reader.TokenType != JsonToken.None);

                                if (reader.TokenType == JsonToken.None)
                                {
                                    Log($"[{id}] reached end of input stream, ending.");
                                    return;
                                }

                                Log("Reading object");
                                var json = await JObject.LoadAsync(reader, ct);
                                Log($"Object read, {ct.IsCancellationRequested}");
                                var request = json.ToObject<ServerObjects.Request>();
                                var functionToExecute = R.HandleRequest(request.Method);
                                
                                // execute right function returned by router or if the router couldn't find a function
                                // return a status code indicating request not found
                                Task<ServerObjects.Response> response;
                                if (functionToExecute != null)
                                {
                                    response = functionToExecute.ReqExecutor != null
                                        ? functionToExecute.ReqExecutor(request, ct)
                                        : functionToExecute.ReqExecutorWithCts(request, cts);
                                }
                                else
                                {
                                    response = Task.FromResult(new ServerObjects.Response{Status = (int) StatusCodes.NO_OP});
                                }

                                Log($"request: {request}");
                                
                                await response.ContinueWith(task => Console.WriteLine($"client id {id}'s status: {task.Result.Status}"), ct);
                                serializer.Serialize(writer, await response);
                                await writer.FlushAsync(ct);
                            }
                            catch (JsonReaderException e)
                            {
                                Log($"[{id}] Error reading JSON: {e.Message}, ending");
                                var response = new ServerObjects.Response
                                {
                                    Status = 400,
                                };
                                serializer.Serialize(writer, response);
                                await writer.FlushAsync(ct);
                                // close the connection because an error may not be recoverable by the reader
                                return;
                            }
                            catch (Exception e)
                            {
                                Log($"[{id}] Exception: {e.Message}, ending");
                                return;
                            }
                        }
                    }
                }
                finally
                {
                    Log($"Ended connection {id}");
                } 
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