using System;
using System.Collections.Concurrent;
using System.Threading;
using System.Threading.Tasks;
using System.Threading.Tasks.Dataflow;
using WebServer;

namespace Synchronizers
{
    public class TransferQueue<T>
    {
        private class Request : BaseRequest
        { 
            public bool _isDone = false;
            
        }

        public string Name { get; set; }

        private readonly BufferBlock<T> queue = new BufferBlock<T>();
        private readonly BufferBlock<Request> _requests = new BufferBlock<Request>();

        private async Task<bool> Put(T message)
        {
            if(queue.Count > 0)
            {
                _requests.TryReceive((r) => r._isDone, out var req);
                req.TryAcquire();
            }
            return await queue.SendAsync(message);
        }

        private bool Transfer(T message) => Put(message).Result;

        public async Task<T> Take() => await queue.ReceiveAsync();

    }

}