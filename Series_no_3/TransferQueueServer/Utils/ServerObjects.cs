using Newtonsoft.Json.Linq;

namespace Utils
{
    public class ServerObjects
    {
        // Represents a request
        public class Request
        {
            public string Method { get; set; }
            public string Path { get; set; }
            public JObject Headers { get; set; }
            public JObject Payload { get; set; }

            public override string ToString()
            {
                return $"Method: {Method}, Path: {Path}, Headers: {Headers}, Payload: {Payload}";
            }
        }
        
        // represents a response
        public class Response
        {
            public int Status { get; set; }
            public JObject Headers { get; set; }
            public JObject Payload { get; set; }
            
            public override string ToString()
            {
                return $"Status: {Status}, Headers: {Headers}, Payload: {Payload}";
            }
        }
    }
}