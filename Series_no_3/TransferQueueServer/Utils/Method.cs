using System;
using System.Threading;
using System.Threading.Tasks;

namespace WebServer
{
    public class Method
    {
        public readonly Func<ServerObjects.Request, CancellationToken, Task<ServerObjects.Response>> ReqExecutor;
        public readonly string MethodName;
        
        public Method(
            Func<ServerObjects.Request, CancellationToken, Task<ServerObjects.Response>> reqExecutor,
            string method)
        {
            ReqExecutor = reqExecutor;
            MethodName = method;
        }
    }
}