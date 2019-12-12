using System.Collections.Generic;
using System.Linq;
using System.Threading;

namespace WebServer
{
    public class Router
    {
        private IEnumerable<Method> _methods = Enumerable.Empty<Method>();
        public Method HandleRequest(string method)
        {
            return _methods.First(m => m.MethodName == method);
        }

        public void InitializeRouterMethods()
        {
            _methods = new []
            {
                new Method(ServerFunctionalities.ExecuteCreate, "CREATE"),
                new Method(ServerFunctionalities.ExecutePut, "PUT"),
                new Method(ServerFunctionalities.ExecuteTransfer, "TRANSFER"),
                new Method(ServerFunctionalities.ExecuteTake, "TAKE"),
                new Method(ServerFunctionalities.ExecuteShutdown, "SHUTDOWN"), 
            };
        }
    }
}