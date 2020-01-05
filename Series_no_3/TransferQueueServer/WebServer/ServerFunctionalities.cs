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
            return new Task<ServerObjects.Response>(() =>
            {
                
                // Create and add a transferQueue to our Queues, has to be thread-safe
                Queues.Enqueue(new TransferQueue<JObject> {Name = request.Path});
                
                return new ServerObjects.Response {Status = (int) StatusCodes.OK};
            });
        }

        public static Task<ServerObjects.Response> ExecutePut(ServerObjects.Request request, CancellationToken ct)
        {

            try
            {
                
                return Task.FromResult( Queues.AsEnumerable(
                    .First(tq => tq.Name.Equals(request.Path)).Put(request.Payload)
                    ? new ServerObjects.Response {Status = (int) StatusCodes.OK}
                    : new ServerObjects.Response {Status = (int) StatusCodes.BAD_REQUEST});

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

                return Task.FromResult<ServerObjects.Response>( Queues.AsEnumerable(
                    .First(tq => tq.Name.Equals(request.Path))
                    .Transfer(request.Payload, 
                        TimeSpan.FromMilliseconds((int) (request.Headers["timeout"] ?? "1000")), ct));

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

                return Task.FromResult( Queues.AsEnumerable(
                    .First(tq => 
                        tq.Name.Equals(request.Path)).Take(
                        TimeSpan.FromMilliseconds((int)  (request.Headers["timeout"] ?? "1000")), ct));

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
            if (!ct.IsCancellationRequested)
            {
                ct.Register()
            }

            return Task.FromResult(new ServerObjects.Response());
        }
    }
}