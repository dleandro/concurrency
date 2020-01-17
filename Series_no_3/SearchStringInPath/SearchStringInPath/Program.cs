using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net.Mime;
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

            Console.WriteLine("Insert string");
            var strToFind = Console.ReadLine();
            
            Console.WriteLine("Insert Path");

            var pathToSearch = Console.ReadLine();
            
            Console.WriteLine("searching for {0} in {1}", strToFind, pathToSearch);
            
            // loop through every directory enumerated including current directory and its sub directories
            // while looping check all files for desired string
            Parallel.ForEach(Directory.EnumerateDirectories(pathToSearch, "*.*",
                SearchOption.AllDirectories), directory => CheckAllFiles(strToFind, directory));
            
            Console.WriteLine("Number of lines read: {0}", _readLinesCounter);

            Console.WriteLine("Number of lines with the string {0}: {1}", strToFind, _readLinesEqualToString);

            Console.WriteLine("Number of files read: {0}", _readFilesCounter);
        }

        private static void CheckAllFiles(string lineToFind, string currPath)
        {
            // Go through every file on the current directory
            Parallel.ForEach(Directory.GetFiles(currPath, "*.txt"), file =>
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