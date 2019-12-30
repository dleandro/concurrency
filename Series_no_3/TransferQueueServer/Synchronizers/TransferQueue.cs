using System;
using System.Collections.Concurrent;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json.Linq;
using Utils;
using WebServer;

namespace Synchronizers
{
    public class TransferQueue<T>
    {
        private class Request : BaseRequest<bool>
        {
            public bool _isDone = false;
        }

        public string Name { get; set; }
        
        // Object used for mutual exclusion while accessing shared data  
        private readonly object _mon = new object();

        private static readonly Task<bool> TrueTask = Task.FromResult(true);
        private static readonly Task<bool> FalseTask = Task.FromResult(false);
        
        private readonly ConcurrentQueue<T> queue = new ConcurrentQueue<T>();
        private readonly ConcurrentQueue<Request> _requests = new ConcurrentQueue<Request>();

        public Task<bool> Put(T message)
        {
            queue.Enqueue(message);
            return TrueTask;
        }
        
        public async Task<ServerObjects.Response> Transfer(T message, TimeSpan timeout, CancellationToken ct)
        {
            
            
        }

        public Task<ServerObjects.Response> Take(TimeSpan timeout, CancellationToken ct)
        {
            if (!queue.IsEmpty)
            {
                return queue.TryDequeue(out var res)
                    ? Task.FromResult(new ServerObjects.Response {Status = (int) StatusCodes.OK, Payload = new JObject(res)})
                    : Task.FromResult(new ServerObjects.Response {Status = (int) StatusCodes.SERVER_ERR});
            }
            
            if (timeout.TotalMilliseconds <= 0)
            {
                return ;
            }
            
            
            
        }
        
    }

}