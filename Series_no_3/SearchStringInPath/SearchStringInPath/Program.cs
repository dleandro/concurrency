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
            var subDirRes = new ParallelLoopResult();
            
            // check first for all sub directories if any and check every file in those
            if (Directory.GetDirectories(args[0]).Length > 0)
            {
                subDirRes = Parallel.ForEach(Directory.EnumerateDirectories(args[0]), directory =>
                {
                    CheckAllFiles(args[1], directory);
                });
            }
            
            // check every file on the first directory
            var dirRes = CheckAllFiles(args[1], args[0]);

            // check this boolean, probably causes concurrency problems 
            if (!dirRes.IsCompleted && !subDirRes.IsCompleted) return;
            
            Console.WriteLine("Number of lines read: {0}", _readLinesCounter);

            Console.WriteLine("Number of lines with the string {0}: {1}", args[1], _readLinesEqualToString);

            Console.WriteLine("Number of files read: {0}", _readFilesCounter);
        }

        private static ParallelLoopResult CheckAllFiles(string lineToFind, string currPath)
        {
            // Go through every file on the current directory
            return Parallel.ForEach(Directory.GetFiles(currPath), file =>
            {
                // read lines    
                ReadCurrPathLines(currPath, lineToFind);
            });
        }

        private static ParallelLoopResult ReadCurrPathLines(string currPath, string stringToFind)
        {
            // increment number of files read
            Interlocked.Increment(ref _readFilesCounter);
            
            return Parallel.ForEach(File.ReadLines(currPath), line =>
            {

                Interlocked.Increment(ref _readLinesCounter);

                if (!line.Contains(stringToFind)) return;
                Interlocked.Increment(ref _readLinesEqualToString);
                Console.WriteLine("line with given string: {0}", line);

            });

        }
    }
}