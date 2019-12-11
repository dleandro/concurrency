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
                new Method(Server.ExecuteCreate, "CREATE"),
                new Method(Server.ExecutePut, "PUT"),
                new Method(Server.ExecuteTransfer, "TRANSFER"),
                new Method(Server.ExecuteTake, "TAKE"),
                new Method(Server.ExecuteShutdown, "SHUTDOWN"), 
            };
        }
    }
}