using System;
using System.Collections.Concurrent;
using System.Threading;
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
        
        // Object used for mutual exclusion while accessing shared data  
        private readonly object _mon = new object();

        private readonly ConcurrentQueue<T> queue = new ConcurrentQueue<T>();
        private readonly ConcurrentQueue<Request> _requests = new ConcurrentQueue<Request>();

        private void Put(T message)
        {
            if (queue.IsEmpty)
            {
                queue.Enqueue(message);
                return;
            }

            lock (_mon)
            {
                _requests.TryDequeue(out var req);
                req.TryAcquire();
                queue.Enqueue(message);
            }
            
        }
        
        private bool Transfer(T message)
        {
            
        }

        private T Take()
        {
            
        }
        
    }

}