% Script to generate Test Data for the DTMF Decoder
% TODO: summary of how the data is produced and variales being
% changed/tested

numFiles = input('Enter the number of files to be generated: ');
folderName = 'Test Data';
sprintf(strcat('Your files will be located in the folder "',folderName,'" inside the directory of this script'))

if (~exist(folderName,'dir'))
    mkdir(folderName);
end
    
for count = 1:numFiles
    [y chars] = randSeq(randomInt(5,20),8000,0.8);
    name = strcat(folderName,'/',chars,'.wav');
    audiowrite(name,y,Fs);
end
    