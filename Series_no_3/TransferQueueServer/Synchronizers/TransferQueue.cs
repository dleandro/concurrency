using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json.Linq;
using Utils;
using WebServer;

namespace Synchronizers
{
    public class TransferQueue<T>
    {
        private class TransferRequest : BaseRequest<ServerObjects.Response>
        {
            public readonly LinkedListNode<T> AddedNode;
            public TransferRequest(LinkedListNode<T> addedNode)
            {
                AddedNode = addedNode;
            }

            public Timer Timer { get; set; }
            public CancellationTokenRegistration CancellationRegistration { get; set; }
        }
        
        private class TakeRequest : BaseRequest<ServerObjects.Response>
        {

            public Timer Timer { get; set; }
            public CancellationTokenRegistration CancellationRegistration { get; set; }
        }

        public string Name { get; set; }
        
        // Object used for mutual exclusion while accessing shared data  
        private readonly object _mon = new object();

        private readonly LinkedList<T> _queue = new LinkedList<T>();
        private readonly LinkedList<TransferRequest> _transferRequests = new LinkedList<TransferRequest>();
        private readonly LinkedList<TakeRequest> _takeRequests = new LinkedList<TakeRequest>();

        public Task<ServerObjects.Response> Put(T message)
        {
            lock (_mon)
            {
                _queue.AddLast(message);
            }

            return Task.FromResult(new ServerObjects.Response { Status = (int)StatusCodes.OK });
        }

        public Task<ServerObjects.Response> Transfer(T message, TimeSpan timeout, CancellationToken ct)
        {
            lock (_mon)
            {
                // check for a pending request
                if (_takeRequests.Count != 0)
                {
                    var removedNode = _takeRequests.First;
                    _takeRequests.Remove(removedNode);
                    removedNode.Value.SetResult(new ServerObjects.Response {Status = (int) StatusCodes.OK, 
                        Payload = new JObject(message)});
                    return Task.FromResult(new ServerObjects.Response {Status = (int) StatusCodes.OK});
                }
                
                var addedNode = _queue.AddLast(message);

                if (timeout.TotalMilliseconds <= 0)
                { 
                    _queue.Remove(addedNode);
                    return Task.FromResult(new ServerObjects.Response {Status = (int) StatusCodes.TIMEOUT});
                }
            
                // create a request
                var request = new TransferRequest(addedNode);
                var node = _transferRequests.AddLast(request);
            
                // register cancellation due to timeout and return
                request.Timer = new Timer(CancelTransferDueToTimeout, node, timeout, new TimeSpan(-1));

                // register cancellation due to cancellation token
                request.CancellationRegistration = ct.Register(() => CancelTransferDueToCancellationToken(node));

                return request.Task;
            }
        }
        
        private void CancelTransferDueToCancellationToken(object state)
        {

            var node = (LinkedListNode<TransferRequest>) state;

            if (!node.Value.TryAcquire())
            {
                // bailing out, some other thread is already dealing with this node
                return;
            }
            
            node.Value.Timer.Dispose();
            Dictionary<TransferRequest, TakeRequest> requestsToComplete;
            
            lock (_mon)
            {
                
                // remove message from queue
                _queue.Remove(node.Value.AddedNode);
                
                // remove request from queue
                _transferRequests.Remove(node);

                // Because a cancellation can complete other consumers
                requestsToComplete = ReleaseRequestsWithinLock();
            }

            // Complete tasks *outside* the lock
            node.Value.SetCanceled();
            
            CompleteRequests(requestsToComplete);
            
        }

        private void CancelTransferDueToTimeout(object state)
        {

            var node = (LinkedListNode<TransferRequest>) state;

            if (!node.Value.TryAcquire())
            {
                // bailing out, some other thread is already dealing with this node
                return;
            }
                
            Dictionary<TransferRequest, TakeRequest> requestsToComplete;
            
            lock (_mon)
            {
                node.Value.CancellationRegistration.Dispose();
                
                // remove message from queue
                _queue.Remove(node.Value.AddedNode);
                
                // remove request from queue
                _transferRequests.Remove(node);

                // Because a cancellation can complete other consumers
                requestsToComplete = ReleaseRequestsWithinLock();
            }

            // Complete tasks *outside* the lock
            node.Value.SetResult(new ServerObjects.Response {Status = (int) StatusCodes.SERVER_ERR});
            
            CompleteRequests(requestsToComplete);
            
        }

        private static void CompleteRequests(Dictionary<TransferRequest, TakeRequest> requestsToComplete)
        {
            foreach (var (transferR, takeR) in requestsToComplete)
            {
                transferR.SetResult(new ServerObjects.Response {Status = (int) StatusCodes.OK});
                takeR.SetResult(new ServerObjects.Response {Status = (int) StatusCodes.OK, Payload = new JObject(transferR.AddedNode)});
            }
        }

        /**
         * Goes through the requests list and removes all requests that can be fulfilled by the messages in the queue.
         * Returns a list with them, so that the task can be completed outside the lock
         * It must always be called with the lock already acquired.
         */
        private Dictionary<TransferRequest, TakeRequest> ReleaseRequestsWithinLock()
        {
            
            var requestsToComplete = new Dictionary<TransferRequest, TakeRequest>();
            
            // while there are requests to be fulfilled
            while (_transferRequests.Count != 0 && _takeRequests.Count != 0)
            {
                var firstTr = _transferRequests.First;
                var firstTa = _takeRequests.First;
                
                if (!firstTa.Value.TryAcquire() && !firstTr.Value.TryAcquire())
                {
                    // Some other thread is already dealing with this nodes, 
                    // so bail out of this. That other thread will remove this nodes and
                    // perform the required processing.
                    break;
                }

                _transferRequests.RemoveFirst();
                _takeRequests.RemoveFirst();
                
                firstTa.Value.Timer.Dispose();
                firstTr.Value.Timer.Dispose();

                firstTa.Value.CancellationRegistration.Dispose();
                firstTr.Value.CancellationRegistration.Dispose();

                requestsToComplete.Add(firstTr.Value, firstTa.Value);
            }

            return requestsToComplete;
        }

        public Task<ServerObjects.Response> Take(TimeSpan timeout, CancellationToken ct)
        {
            lock (_mon)
            {
                
                // check if there are messages available and requests to be fulfilled
                if (_queue.Count > 0 && _transferRequests.Count > 0)
                {
                    var removedRequest = _transferRequests.First;
                    _transferRequests.Remove(removedRequest);
                    removedRequest.Value.SetResult(new ServerObjects.Response {Status = (int) StatusCodes.OK});
                    
                    _queue.Remove(removedRequest.Value.AddedNode);
                    
                    return Task.FromResult(new ServerObjects.Response
                        {Status = (int) StatusCodes.OK, Payload = new JObject(removedRequest.Value.AddedNode.Value)});
                }

                if (timeout.TotalMilliseconds <= 0)
                {
                    return Task.FromResult(new ServerObjects.Response {Status = (int) StatusCodes.TIMEOUT});
                }

                // create a request
                var request = new TakeRequest();
                var node = _takeRequests.AddLast(request);
                
                request.Timer = new Timer(CancelTakeDueToTimeout, node, timeout, new TimeSpan(-1));
                request.CancellationRegistration = ct.Register(CancelTakeDueToCancellationToken, node);
                return request.Task;
            }
        }

        private void CancelTakeDueToCancellationToken(object state)
        {
            var node = (LinkedListNode<TakeRequest>) state;

            if (!node.Value.TryAcquire())
            {
                // bailing out, some other thread is already dealing with this node
                return;
            }

            node.Value.Timer.Dispose();
            Dictionary<TransferRequest, TakeRequest> requestsToComplete;
            lock (_mon)
            {
                _takeRequests.Remove(node);
                // Because a cancellation can complete other consumers
                requestsToComplete = ReleaseRequestsWithinLock();
            }

            // Complete tasks *outside* the lock
            node.Value.SetCanceled();
            CompleteRequests(requestsToComplete);
        }

        private void CancelTakeDueToTimeout(object state)
        {
            var node = (LinkedListNode<TakeRequest>) state;

            if (!node.Value.TryAcquire())
            {
                // bailing out, some other thread is already dealing with this node
                return;
            }

            Dictionary<TransferRequest, TakeRequest> requestsToComplete;
            lock (_mon)
            {
                node.Value.CancellationRegistration.Dispose();
                _takeRequests.Remove(node);
                // Because a cancellation can complete other consumers
                requestsToComplete = ReleaseRequestsWithinLock();
            }

            // Complete tasks *outside* the lock
            node.Value.SetResult(new ServerObjects.Response{ Status = (int) StatusCodes.SERVER_ERR});
            CompleteRequests(requestsToComplete);
        }
    }
}