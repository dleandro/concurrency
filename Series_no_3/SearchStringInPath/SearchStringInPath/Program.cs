using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;

namespace SearchStringInPath
{
    class Program
    {
        
        private static int _readFilesCounter;
        private static int _readLinesCounter;
        private static int _readLinesEqualToString;

        private static void Main(string[] args)
        {

            // loop through every directory enumerated including current directory and its sub directories
            // while looping check all files for desired string
            var res = Parallel.ForEach(Directory.EnumerateDirectories(args[0], "*.*",
                SearchOption.AllDirectories), directory => CheckAllFiles(args[1], directory));

            while (!res.IsCompleted) ;
            
            Console.WriteLine("Number of lines read: {0}", _readLinesCounter);

            Console.WriteLine("Number of lines with the string {0}: {1}", args[1], _readLinesEqualToString);

            Console.WriteLine("Number of files read: {0}", _readFilesCounter);
        }

        private static void CheckAllFiles(string lineToFind, string currPath)
        {
            // Go through every file on the current directory
            Parallel.ForEach(Directory.GetFiles(currPath), file =>
            {
                // increment number of files read
                Interlocked.Increment(ref _readFilesCounter);
                
                // read lines    
                ReadCurrPathLines(currPath, lineToFind);
            });
        }

        private static void ReadCurrPathLines(string currPath, string stringToFind)
        {
            
            Parallel.ForEach(File.ReadLines(currPath), line =>
            {

                Interlocked.Increment(ref _readLinesCounter);

                if (!line.Contains(stringToFind)) return;
                Interlocked.Increment(ref _readLinesEqualToString);
                Console.WriteLine("line with given string: {0}", line);

            });

        }
    }
}