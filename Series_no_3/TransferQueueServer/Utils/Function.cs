using System;
using System.Threading;
using System.Threading.Tasks;

namespace Utils
{
    public class Function
    {
        public readonly Func<ServerObjects.Request, CancellationToken, Task<ServerObjects.Response>> ReqExecutor;
        public readonly Func<ServerObjects.Request, CancellationTokenSource, Task<ServerObjects.Response>> ReqExecutorWithCts;
        public readonly string MethodName;
        
        public Function(
            Func<ServerObjects.Request, CancellationToken, Task<ServerObjects.Response>> reqExecutor,
            string method)
        {
            ReqExecutor = reqExecutor;
            MethodName = method;
        }
        
        public Function(
            Func<ServerObjects.Request, CancellationTokenSource, Task<ServerObjects.Response>> reqExecutorWithCts,
            string method)
        {
            ReqExecutorWithCts = reqExecutorWithCts;
            MethodName = method;
        }
        
    }
}