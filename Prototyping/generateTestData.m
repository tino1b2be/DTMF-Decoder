% Script to generate Test Data for the DTMF Decoder
% TODO: summary of how the data is produced and variales being
% changed/tested

numFiles = input('Enter the number of files to be generated for each power range: ');
Fs = input('Enter the sampling Frequency: ');
folderName = 'Test Data';
disp(strcat('Your files will be located in the folder "',folderName,'" inside the directory of this script'));

if (~exist(folderName,'dir'))
    mkdir(folderName);
    mkdir(folderName,'/-1dBm to 0dBm');
    mkdir(folderName,'/-3dBm to -1dBm');
    mkdir(folderName,'/-10dBm to -3dBm');
    mkdir(folderName,'/-27dBm to -10dBm');
else
    if (~exist(strcat(folderName,'/-1dBm to 0dBm'),'dir'))
        mkdir(folderName,'/-1dBm to 0dBm');
    end
    if (~exist(strcat(folderName,'/-3dBm to -1dBm'),'dir'))
        mkdir(folderName,'/-3dBm to -1dBm');
    end
    if (~exist(strcat(folderName,'-10dBm to -3dBm'),'dir'))
        mkdir(folderName,'/-10dBm to -3dBm');
    end
    if (~exist(strcat(folderName,'-27dBm to -10dBm'),'dir'))
        mkdir(folderName,'/-27dBm to -10dBm');
    end
end
    

% full power , amplitude : 0.9 to 1.0 , power = -1dbm to 0dbm
for count = 1:numFiles
    [y, chars] = randSeq(randomInt(5,50),8000,randomInt(9000,10000)/10000);
    name = strcat(folderName,'/-1dBm to 0dBm/',chars,'.wav');
    audiowrite(name,y,Fs);
    if (count == floor(numFiles/2))
        disp('Halfway through the first power range: -1 to 0dbm');
    end
end

disp('Done with the first power range');

% -3dbm to -1dbm, amplitude : 0.7 - 0.9
for count = 1:numFiles
    [y, chars] = randSeq(randomInt(5,20),8000,randomInt(7000,9000)/10000);
    name = strcat(folderName,'/-3dBm to -1dBm/',chars,'.wav');
    audiowrite(name,y,Fs);
    if (count == floor(numFiles/2))
        disp('Halfway through the second power range: -3 to 1dbm');
    end
end

disp('Done with the second power range');

% -10dbm to -3dbm, amplitude: 0.3 - 0.7
for count = 1:numFiles
    [y, chars] = randSeq(randomInt(5,20),8000,randomInt(3000,7000)/10000);
    name = strcat(folderName,'/-10dBm to -3dBm/',chars,'.wav');
    audiowrite(name,y,Fs);
    if (count == floor(numFiles/2))
        disp('Halfway through the first power range: -10 to -3dbm');
    end
end

disp('Done with the third power range');

% -30dbm to -10dbm, amplitude: 0.045 - 0.3
for count = 1:numFiles
    [y, chars] = randSeq(randomInt(5,20),8000,randomInt(450,3000)/10000);
    name = strcat(folderName,'/-27dBm to -10dBm/',chars,'.wav');
    audiowrite(name,y,Fs);
    if (count == floor(numFiles/2))
        disp('Halfway through the fourth power range: -27 to -10dbm');
    end
end

disp('Done with the forth power range');
