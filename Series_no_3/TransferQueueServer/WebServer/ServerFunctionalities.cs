using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json.Linq;
using Synchronizers;

namespace WebServer
{
    public class ServerFunctionalities
    {
        private static readonly ConcurrentQueue<TransferQueue<JObject>> Queues = new ConcurrentQueue<TransferQueue<JObject>>();

        public static Task<ServerObjects.Response> ExecuteCreate(ServerObjects.Request request, CancellationToken ct)
        {
            // Create and add a transferQueue to our Queues, has to be thread-safe
            Queues.Enqueue(new TransferQueue<JObject> {Name = request.Path});

            return new Task<ServerObjects.Response>(() => new ServerObjects.Response());
        }

        public static Task<ServerObjects.Response> ExecutePut(ServerObjects.Request arg1, CancellationToken arg2)
        {
            throw new System.NotImplementedException();
        }

        public static Task<ServerObjects.Response> ExecuteTransfer(ServerObjects.Request arg1, CancellationToken arg2)
        {
            throw new System.NotImplementedException();
        }

        public static Task<ServerObjects.Response> ExecuteTake(ServerObjects.Request arg1, CancellationToken arg2)
        {
            throw new System.NotImplementedException();
        }
        
        public static async Task<ServerObjects.Response> ExecuteShutdown(ServerObjects.Request request, CancellationToken ct)
        {
            if (ct.CanBeCanceled)
            {
                // start wait until it is cancelled
                var cts = new CancellationTokenSource();
                ct = cts.Token;
                cts.Cancel();
            }
            return new ServerObjects.Response();
        }
    }
}