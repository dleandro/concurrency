using System.Collections.Generic;
using System.Linq;
using Utils;

namespace WebServer
{
    public class Router
    {
        private IEnumerable<Function> _methods = Enumerable.Empty<Function>();
        public Function HandleRequest(string method)
        {
            return _methods.First(m => m.MethodName == method);
        }

        public void InitializeRouterMethods()
        {
            _methods = new []
            {
                new Function(ServerFunctionalities.ExecuteCreate, "CREATE"),
                new Function(ServerFunctionalities.ExecutePut, "PUT"),
                new Function(ServerFunctionalities.ExecuteTransfer, "TRANSFER"),
                new Function(ServerFunctionalities.ExecuteTake, "TAKE"),
                new Function(ServerFunctionalities.ExecuteShutdown, "SHUTDOWN"), 
            };
        }
    }
}