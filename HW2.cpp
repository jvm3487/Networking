#include <iostream>
#include <fstream>
#include <cstdlib>
#include <map>
#include <vector>
#include <sstream>
#include <string>
#include <climits>
#include <string.h>

struct _otherData
{
  std::string strFrom;
  std::string nextHop;
  std::vector<unsigned> asPath;
  bool bIGP;
  int MED;
  bool announceType;
  unsigned timeSec;
  _otherData(): bIGP(false), MED(INT_MAX), announceType(false), timeSec(0) {}
};
typedef struct _otherData otherData;

std::string checkSmallerIPValue(const std::string& newValue, const std::string& oldValue)
{
  if (oldValue.empty())
    return newValue;

  for (unsigned newIndex = 0, oldIndex = 0; newIndex < newValue.size() || oldIndex < oldValue.size(); newIndex++ , oldIndex++)
    {
      std::stringstream newStream;
      for ( ; newIndex < newValue.size() && newValue[newIndex] != '.' && newValue[newIndex] != ':' && newValue[newIndex] != '\0'; newIndex++)
	newStream << newValue[newIndex];

      std::stringstream oldStream;
      for ( ; oldIndex < oldValue.size() && oldValue[oldIndex] != '.' && oldValue[oldIndex] != ':' && oldValue[oldIndex] != '\0'; oldIndex++)
	oldStream << oldValue[oldIndex];

      unsigned baseType = 0;
      if (newValue.find(":")) //check if IPv6
	baseType = 16;
      else
	baseType = 10;
	
      unsigned uNew = newStream.rdbuf()->in_avail() ? std::stoi(newStream.str(), nullptr, baseType) : 0;
      unsigned uOld = oldStream.rdbuf()->in_avail() ? std::stoi(oldStream.str(), nullptr, baseType) : 0;

      if (uNew < uOld)
	return newValue;
      if (uOld < uNew)
	return oldValue;

    }
  return oldValue;
}

void parseInput(const std::string& line, std::vector<std::string>& changeIP, otherData& oneBlockData)
{
  // Gather the time in sec from the beginning of the day
  if (line.find("TIME:") != std::string::npos)
    {
      for (unsigned strIndex = 15; strIndex < line.size(); strIndex++)
	{
	  std::stringstream timeStream;
	  for ( ; line[strIndex] != ':' && line[strIndex] != '\0'; strIndex++)
	    timeStream << line[strIndex];

	  if (strIndex == 17) // hours
	    oneBlockData.timeSec = std::stoi(timeStream.str()) * 3600;
	  else if (strIndex == 20) // minutes
	    oneBlockData.timeSec += std::stoi(timeStream.str()) * 60;
	  else // seconds
	    oneBlockData.timeSec += std::stoi(timeStream.str());
	}
    }

  // Gather the FROM address to check in the case of withdraw
  else if (line.find("FROM:") != std::string::npos)
    {
      std::stringstream fromIP;	  
      for (unsigned strIndex = 6; line[strIndex] != ' ' && line[strIndex] != '\0'; strIndex++)
	fromIP << line[strIndex];
      oneBlockData.strFrom = fromIP.str();
    }

  // Gather the Next Hop IP Address
  else if (line.find("NEXT_HOP:") != std::string::npos)
    {
      std::stringstream destIP;	  
      for (unsigned strIndex = 10; line[strIndex] != ' ' && line[strIndex] != '\0'; strIndex++)
	destIP << line[strIndex];
      std::string stringNewIP = destIP.str();
      // Check if the new string is smaller than the old string
      oneBlockData.nextHop = checkSmallerIPValue(stringNewIP, oneBlockData.nextHop);
    }
      
  // Gather the origin information
  else if (line.find("ORIGIN:") != std::string::npos)
    oneBlockData.bIGP = (line.find("IGP") != std::string::npos);

  // Gather the ASPATH
  else if (line.find("ASPATH:") != std::string::npos)
    {
      for (unsigned currentIndex = 8 ; currentIndex < line.size() ; currentIndex++)
	{
	  std::stringstream oneASpath;
	  for ( ; line[currentIndex] != ' ' && line[currentIndex] != '\0' && line[currentIndex] != '{' && line[currentIndex] != ',' && line[currentIndex] != '}'; currentIndex++)
	    oneASpath << line[currentIndex];
	  if (oneASpath.rdbuf()->in_avail())
	    oneBlockData.asPath.push_back(std::stoi(oneASpath.str()));
	}
    } 
  
  // Gather the MED
  else if (line.find("MULTI_EXIT_DISC:") != std::string::npos)
    {
      std::stringstream medStream;
      for (unsigned strIndex = 17; line[strIndex] != ' ' && line[strIndex] != '\0'; strIndex++)
	medStream << line[strIndex];
      oneBlockData.MED = std::stoi(medStream.str());
    }

  // Determine if announce
  else if (line.find("ANNOUNCE") !=std::string::npos)
    oneBlockData.announceType = true;
  
  // Determine announced ip addresses
  else if (line[0] == ' ' && line[2] != ' ')
    {
      std::stringstream ipAdd;
      for (unsigned currentIndex = 2 ; currentIndex < line.size() && line[currentIndex] != '\0'; currentIndex++)
	ipAdd << line[currentIndex];
      changeIP.push_back(ipAdd.str());
    }
  
}

void updateMapWithNewInfo(std::map<std::string, otherData>& mapIPAddress, const std::string& changeIPStr, const otherData& oneBlockData)
{
  std::map<std::string, otherData>::iterator it;
  it = mapIPAddress.find(changeIPStr);
  
  if (oneBlockData.announceType)
    {
      // check to see if the information should be updated or left as is
      if (it != mapIPAddress.end())
	{
	  // first check - AS path length
	  if (it->second.asPath.size() < oneBlockData.asPath.size())
	    return;
	  else if (it->second.asPath.size() == oneBlockData.asPath.size()) //same - go to next tiebreaker
	    {
	      // second check - better not IGP
	      if (it->second.bIGP == false && oneBlockData.bIGP == true)
		return;
	      else if (it->second.bIGP == oneBlockData.bIGP) // same - go to next tiebreaker
		{
		  // third check - MED
		  if (it->second.MED < oneBlockData.MED)
		    return;
		  else if (it->second.MED == oneBlockData.MED) // same - go to final tiebreak
		    {
		      std::string smallestIP = checkSmallerIPValue(oneBlockData.nextHop, it->second.nextHop);
		      if (smallestIP.compare(it->second.nextHop) == 0)
			return;
		    }
		  
		}
	    }
	  it->second = oneBlockData;
	}
      else
	{
	  mapIPAddress.emplace(changeIPStr, oneBlockData);
	}
    }
  else
    {
      // compare withdraw with next hop ip to make sure it came from the same router that originally published it
      if (it != mapIPAddress.end() && it->second.strFrom.compare(oneBlockData.strFrom) == 0)
	mapIPAddress.erase(it);
    }
}

int main (int argc, char* argv[])
{
  bool inputFailure = true;
  std::string mode;
  std::ifstream inputFile;
  if (argc < 5)
    {
      std::cerr << "Less than 5 arguments" << std::endl;
    }
  else if (strcmp(argv[1], "-m"))
    {
      std::cerr << "Expected -m as second input argument" << std::endl;
    }
  else if (strcmp(argv[2], "1") && strcmp(argv[2], "2a") && strcmp(argv[2], "2b"))
    {
      std::cerr << "Expected mode 1, 2a, or 2b" << std::endl;
    }
  else if (strcmp(argv[3], "-f"))
    {
      std::cerr << "Expected -f as fourth input argument" << std::endl;
    }
  else
    {
      inputFile.open(argv[4]);
      if (!inputFile.good())
	{
	  std::cerr << "Error reading input file" << std::endl;
	  inputFile.close();
	}
      else
	{
	  mode = argv[2];
	  inputFailure = false;
	}
    }

  if (inputFailure)
    {
      std::cerr << "Expected arguments ./HW2 -m mode{1,2a,2b} -f bgp-update-file" << std::endl;
      return EXIT_FAILURE;
    }

  // IP Address map to
  // Next Hop IP Address, AS path, origin type IDP=true, MED, and announceType
  std::map<std::string, otherData > mapIPAddress;

  // For 2a and 2b withdrawal counting
  //std::vector<std::string> ip60Sec[60];
  unsigned numberOfWithdrawals[60];
  for (unsigned i = 0; i < 60; i++)
    numberOfWithdrawals[i] = 0;
  unsigned currentTimeBase = 0;
  unsigned maxLastMinute = 0;
  unsigned maxTimeInstance = 0;

  std::string line;
 
  while (!inputFile.eof())
    {
  
    std::vector<std::string> changeIP;
    otherData oneBlockData;

    while (std::getline(inputFile, line))
      {
      	
	if (line.empty() || inputFile.eof())
	  {
	    break;
	  }
	else
	  {
	    parseInput(line, changeIP, oneBlockData);
	  }
      }
       
    if (mode.compare("2b") == 0 && !oneBlockData.announceType)
      {
	for (const auto& changeIPStr: changeIP){
	// find the address in the map
	std::map<std::string, otherData>::iterator it;
	it = mapIPAddress.find(changeIPStr);

	if (it == mapIPAddress.end())
	  continue;

	bool continueLoop = true;
	std::cout << oneBlockData.asPath.size() << std::endl;
	for (const auto& currentASpath : it->second.asPath)
	  {
	    std::cout << currentASpath << std::endl;
	    if (currentASpath == 29256 || currentASpath == 29386)
	      continueLoop = false;
	  }

	if (continueLoop)
	  continue;

	std::cout << "I am here" << std::endl;

	if (currentTimeBase == 0)
	  currentTimeBase = oneBlockData.timeSec;

	if (currentTimeBase == oneBlockData.timeSec)
	  {
	    numberOfWithdrawals[0] += changeIP.size(); 
	  }
	else
	  {
	    unsigned totalWithdrawalsLastMinute = 0;
	    for (unsigned i = 0; i < 60; i++)
	      totalWithdrawalsLastMinute += numberOfWithdrawals[i];

	    std::cout << totalWithdrawalsLastMinute << std::endl;

	    if (totalWithdrawalsLastMinute > maxLastMinute)
	      {
		maxTimeInstance = currentTimeBase;
		maxLastMinute = totalWithdrawalsLastMinute;
	      }

	    unsigned timeChange = oneBlockData.timeSec - currentTimeBase;
	    if (timeChange < 60)
	      {
		// move everything back to reflect the new current time base
		for (unsigned i = timeChange; i < 60; i++)
		  numberOfWithdrawals[i] = numberOfWithdrawals[i + timeChange];

		// put zeros in the other places
		for (unsigned i = 0; i < timeChange; i++)
		  numberOfWithdrawals[i] = 0;
	      }

	    currentTimeBase = oneBlockData.timeSec;

	    numberOfWithdrawals[0] += changeIP.size(); 
	  }
      }
      }
    // update the map with the changed IP address
    for (unsigned i = 0; i < changeIP.size(); i++)
      {
	updateMapWithNewInfo(mapIPAddress, changeIP[i], oneBlockData);
      }
   
    }

  std::cout << maxTimeInstance << std::endl;

  if (mode.compare("1") == 0)
    {
      // print out the map in the correct format
      for (const auto& mapValue : mapIPAddress)
	{
	  std::cout << mapValue.first << " " << mapValue.second.nextHop;
	  for (const auto& asPathValue : mapValue.second.asPath)
	    {
	      std::cout << " " << asPathValue; 
	    }
	  std::cout << std::endl;
	} 
    }

  inputFile.close();

  return EXIT_SUCCESS;
}
