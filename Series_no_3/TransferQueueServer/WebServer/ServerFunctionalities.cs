using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json.Linq;
using Synchronizers;
using static WebServer.ServerObjects;

namespace WebServer
{
    public class ServerFunctionalities
    {
        private static readonly ConcurrentQueue<TransferQueue<JObject>> Queues = new ConcurrentQueue<TransferQueue<JObject>>();
       // private static IAsyncEnumerable<ConcurrentQueue<JObject>> queues;

        public static Task<Response> ExecuteCreate(Request request, CancellationToken ct)
        {
            // Create and add a transferQueue to our Queues, has to be thread-safe
            Queues.Enqueue(new TransferQueue<JObject> { Name = request.Path });

           // queues = of(new ConcurrentQueue<JObject>(new List<JObject>() { JObject.Parse($" 'Name' : '{request.Path}' ") }));

            return new Task<Response>(() => new Response());
        }

        public static Task<Response> ExecutePut(Request arg1, CancellationToken arg2)
        {
            throw new System.NotImplementedException();
        }

        public static Task<Response> ExecuteTransfer(Request arg1, CancellationToken arg2)
        {
            throw new System.NotImplementedException();
        }

        public static Task<Response> ExecuteTake(Request arg1, CancellationToken arg2)
        {
            throw new System.NotImplementedException();
        }
        
        public static async Task<Response> ExecuteShutdown(Request request, CancellationToken ct)
        {
            if (ct.CanBeCanceled)
            {
                // start wait until it is cancelled
                using (var cts = new CancellationTokenSource())
                {
                    ct = cts.Token;
                    cts.Cancel();
                    
                }                
               
            }
            return new Response();
        }


        private static async IAsyncEnumerable<ConcurrentQueue<JObject>> of(params ConcurrentQueue<JObject>[] values)
        {
            foreach(ConcurrentQueue<JObject> value in values)
            {
                yield return value;
            }
        }
    }
}