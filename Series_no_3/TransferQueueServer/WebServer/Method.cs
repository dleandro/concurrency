using System;
using System.Threading;

namespace WebServer
{
    public class Method
    {
        public readonly Func<ServerObjects.Request, CancellationToken, ServerObjects.Response> ReqExecutor;
        public readonly string MethodName;
        
        public Method(
            Func<ServerObjects.Request, CancellationToken, ServerObjects.Response> reqExecutor,
            string method)
        {
            ReqExecutor = reqExecutor;
            MethodName = method;
        }
    }
}