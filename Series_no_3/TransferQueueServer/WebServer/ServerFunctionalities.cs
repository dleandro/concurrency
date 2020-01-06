using System;
using System.Collections.Concurrent;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json.Linq;
using Synchronizers;
using Utils;

namespace WebServer
{
    public static class ServerFunctionalities
    {
        private static readonly ConcurrentQueue<TransferQueue<JObject>> Queues = new ConcurrentQueue<TransferQueue<JObject>>();

        public static Task<ServerObjects.Response> ExecuteCreate(ServerObjects.Request request, CancellationToken ct)
        {
            try
            {
                return new Task<ServerObjects.Response>(() =>
                {
                    ct.ThrowIfCancellationRequested();
                    // Create and add a transferQueue to our Queues, has to be thread-safe
                    Queues.Enqueue(new TransferQueue<JObject> { Name = request.Path });

                    ct.ThrowIfCancellationRequested();
                    return new ServerObjects.Response { Status = (int)StatusCodes.OK };
                });
            }catch(OperationCanceledException e)
            {
                Console.WriteLine("Queue creation cancelled, please try again");
                return Task<ServerObjects.Response>.FromResult(new ServerObjects.Response { Status = (int)StatusCodes.SERVER_ERR });
            }
        }

        public static Task<ServerObjects.Response> ExecutePut(ServerObjects.Request request, CancellationToken ct)
        {

            try
            {

                return Queues.AsEnumerable()
                         .First(tq => tq.Name.Equals(request.Path))
                         .Put(request.Payload);

            }
                
            catch (Exception e)
            {
                Console.WriteLine(e);
                return Task.FromResult(new ServerObjects.Response {Status = (int) StatusCodes.NO_QUEUE});

            }
        }

        public static Task<ServerObjects.Response> ExecuteTransfer(ServerObjects.Request request,
            CancellationToken ct)
        {
            try
            {

                return Queues.AsEnumerable()
                    .First(tq => tq.Name.Equals(request.Path))
                    .Transfer(request.Payload,
                        TimeSpan.FromMilliseconds((int)(request.Headers["timeout"] ?? "1000")), ct);
            } 
            catch (Exception e)
            {
                Console.WriteLine(e);
                return Task.FromResult(new ServerObjects.Response {Status = (int) StatusCodes.NO_QUEUE});

            }
        }

        public static Task<ServerObjects.Response> ExecuteTake(ServerObjects.Request request, CancellationToken ct)
        {
            try
            {

                return Queues.AsEnumerable()
                     .First(tq =>
                         tq.Name.Equals(request.Path)).Take(
                         TimeSpan.FromMilliseconds((int)(request.Headers["timeout"] ?? "1000")), ct);

            }
                
            catch (Exception e)
            {
                Console.WriteLine(e);
                return Task.FromResult(new ServerObjects.Response {Status = (int) StatusCodes.NO_QUEUE});

            }        
        }

        public static Task<ServerObjects.Response> ExecuteShutdown(ServerObjects.Request request,
            CancellationToken ct)
        {
            using (var cancellationTokenSource = new CancellationTokenSource(/*timeSpan*/))
            {
                try
                {
                    ct.ThrowIfCancellationRequested();
                    return Task.FromResult(new ServerObjects.Response { Status = (int)StatusCodes.OK });
                }
                catch (OperationCanceledException e)
                {
                    cancellationTokenSource.Cancel();
                    Console.WriteLine("shutdown initiated...");
                    return Task.FromResult(new ServerObjects.Response { Status = (int)StatusCodes.NO_SERVICE });
                }

            }
        }
    }
}