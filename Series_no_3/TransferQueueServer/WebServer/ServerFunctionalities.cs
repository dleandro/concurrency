using System.Threading;
using System.Threading.Tasks;

namespace WebServer
{
    public class Server
    {
        public static ServerObjects.Response ExecuteCreate(ServerObjects.Request arg1, CancellationToken arg2)
        {
            throw new System.NotImplementedException();
        }

        public static ServerObjects.Response ExecutePut(ServerObjects.Request arg1, CancellationToken arg2)
        {
            throw new System.NotImplementedException();
        }

        public static ServerObjects.Response ExecuteTransfer(ServerObjects.Request arg1, CancellationToken arg2)
        {
            throw new System.NotImplementedException();
        }

        public static ServerObjects.Response ExecuteTake(ServerObjects.Request arg1, CancellationToken arg2)
        {
            throw new System.NotImplementedException();
        }
        
        public async Task<ServerObjects.Response> ExecuteShutdown(ServerObjects.Request request, CancellationToken ct)
        {
            if (ct.CanBeCanceled)
            {
                // start wait until it is cancelled
                var cts = new CancellationTokenSource();
                ct = cts.Token;
                cts.Cancel();
            }
        }
        
        private static async Task<ServerObjects.Response> Delay(ServerObjects.Request request)
        {
            var delayString = request.Headers["timeout"] ?? "1000";
            if (!int.TryParse((string) delayString, out var delay))
            {
                return new ServerObjects.Response
                {
                    Status = 400
                };
            }

            await Task.Delay(delay);
            return new ServerObjects.Response
            {
                Status = 200
            };
        }
    }
}