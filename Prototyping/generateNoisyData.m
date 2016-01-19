% Script to generate Test Data for the DTMF Decoder
% TODO: summary of how the data is produced and variales being
% changed/tested
tic
numFiles = input('Enter the number of files to be generated for each power range: ');
Fs = input('Enter the sampling Frequency: ');
folderName = 'Noisy Test Data';
disp(strcat('Your files will be located in the folder "',folderName,'" inside the directory of this script'));

if (~exist(folderName,'dir'))
    mkdir(folderName);
%     mkdir(folderName,'/0.5dB');
%     mkdir(folderName,'/1dB');
%     mkdir(folderName,'/2dB');
%     mkdir(folderName,'/5dB');
    mkdir(folderName,'/3dB');
    mkdir(folderName,'/6dB');
%     mkdir(folderName,'/10dB');
    mkdir(folderName,'/9dB');
    mkdir(folderName,'/13dB');
    mkdir(folderName,'/17dB');
%     mkdir(folderName,'/30dB');
%     mkdir(folderName,'/40dB');
%     mkdir(folderName,'/50dB');
%     mkdir(folderName,'/60dB');

else
%     if (~exist(strcat(folderName,'/0.5dB'),'dir'))
%         mkdir(folderName,'/0.5dB');
%     end
%     if (~exist(strcat(folderName,'/1dB'),'dir'))
%         mkdir(folderName,'/1dB');
%     end
%     if (~exist(strcat(folderName,'/2dB'),'dir'))
%         mkdir(folderName,'/2dB');
%     end
%     if (~exist(strcat(folderName,'/5dB'),'dir'))
%         mkdir(folderName,'/5dB');
%     end
    if (~exist(strcat(folderName,'/3dB'),'dir'))
        mkdir(folderName,'/3dB');
    end
    if (~exist(strcat(folderName,'/6dB'),'dir'))
        mkdir(folderName,'/6dB');
    end
%     if (~exist(strcat(folderName,'/10dB'),'dir'))
%         mkdir(folderName,'/10dB');
%     end
    if (~exist(strcat(folderName,'/9dB'),'dir'))
        mkdir(folderName,'/9dB');
    end
    if (~exist(strcat(folderName,'/13dB'),'dir'))
        mkdir(folderName,'/13dB');
    end
    if (~exist(strcat(folderName,'/17dB'),'dir'))
        mkdir(folderName,'/17dB');
    end
%     if (~exist(strcat(folderName,'/30dB'),'dir'))
%         mkdir(folderName,'/30dB');
%     end
%     if (~exist(strcat(folderName,'/40dB'),'dir'))
%         mkdir(folderName,'/30dB');
%     end
%     if (~exist(strcat(folderName,'/50dB'),'dir'))
%         mkdir(folderName,'/30dB');
%     end
%     if (~exist(strcat(folderName,'/60dB'),'dir'))
%         mkdir(folderName,'/30dB');
%     end
end

% parfor count = 1:numFiles
%     [x, chars] = randSeq(randomInt(5,50),Fs,1);
%     y = awgn(x,0.5);
%     name = strcat(folderName,'/0.5dB/',chars,'.wav');
%     if (exist(name,'file'))
%     %    count = count - 1;
%         continue;
%     else
%         audiowrite(name,y,Fs);
%     end
%     if (count == floor(numFiles/2))
%         disp('Halfway through 0.5dB');
%     end
% end
% 
% disp('Done with the first SNR');
% 
% parfor count = 1:numFiles
%     [x, chars] = randSeq(randomInt(5,50),Fs,1);
%     y = awgn(x,1);
%     name = strcat(folderName,'/1dB/',chars,'.wav');
%     if (exist(name,'file'))
%     %    count = count - 1;
%         continue;
%     else
%         audiowrite(name,y,Fs);
%     end
%     if (count == floor(numFiles/2))
%         disp('Halfway through 1dB');
%     end
% end
% 
% disp('Done with the second SNR');
% 
% parfor count = 1:numFiles
%     [x, chars] = randSeq(randomInt(5,50),Fs,1);
%     y = awgn(x,2);
%     name = strcat(folderName,'/2dB/',chars,'.wav');
%     if (exist(name,'file'))
%     %    count = count - 1;
%         continue;
%     else
%         audiowrite(name,y,Fs);
%     end
%     if (count == floor(numFiles/2))
%         disp('Halfway through 2dB');
%     end
% end
% 
% disp('Done with the third SNR');

% parfor count = 1:numFiles
%     [x, chars] = randSeq(randomInt(5,50),Fs,1);
%     y = awgn(x,5);
%     name = strcat(folderName,'/5dB/',chars,'.wav');
%     if (exist(name,'file'))
%     %    count = count - 1;
%         continue;
%     else
%         audiowrite(name,y,Fs);
%     end
%     if (count == floor(numFiles/2))
%         disp('Halfway through 5dB');
%     end
% end


% parfor count = 1:numFiles
%     [x, chars] = randSeq(randomInt(5,50),Fs,1);
%     y = awgn(x,5);
%     name = strcat(folderName,'/5dB/',chars,'.wav');
%     if (exist(name,'file'))
%     %    count = count - 1;
%         continue;
%     else
%         audiowrite(name,y,Fs);
%     end
%     if (count == floor(numFiles/2))
%         disp('Halfway through 5dB');
%     end
% end

parfor count = 1:numFiles
    [x, chars] = randSeq(randomInt(5,50),Fs,1);
    y = awgn(x,3);
    name = strcat(folderName,'/3dB/',chars,'.wav');
    if (exist(name,'file'))
    %    count = count - 1;
        continue;
    else
        audiowrite(name,y,Fs);
    end
    if (count == floor(numFiles/2))
        disp('Halfway through 3dB');
    end
end

parfor count = 1:numFiles
    [x, chars] = randSeq(randomInt(5,50),Fs,1);
    y = awgn(x,6);
    name = strcat(folderName,'/6dB/',chars,'.wav');
    if (exist(name,'file'))
    %    count = count - 1;
        continue;
    else
        audiowrite(name,y,Fs);
    end
    if (count == floor(numFiles/2))
        disp('Halfway through 6dB');
    end
end

% parfor count = 1:numFiles
%     [x, chars] = randSeq(randomInt(5,50),Fs,1);
%     y = awgn(x,10);
%     name = strcat(folderName,'/10dB/',chars,'.wav');
%     if (exist(name,'file'))
%     %    count = count - 1;
%         continue;
%     else
%         audiowrite(name,y,Fs);
%     end
%     if (count == floor(numFiles/2))
%         disp('Halfway through 10dB');
%     end
% end

% parfor count = 1:numFiles
%     [x, chars] = randSeq(randomInt(5,50),Fs,1);
%     y = awgn(x,10);
%     name = strcat(folderName,'/10dB/',chars,'.wav');
%     if (exist(name,'file'))
%     %    count = count - 1;
%         continue;
%     else
%         audiowrite(name,y,Fs);
%     end
%     if (count == floor(numFiles/2))
%         disp('Halfway through 10dB');
%     end
% end

parfor count = 1:numFiles
    [x, chars] = randSeq(randomInt(5,50),Fs,1);
    y = awgn(x,9);
    name = strcat(folderName,'/9dB/',chars,'.wav');
    if (exist(name,'file'))
    %    count = count - 1;
        continue;
    else
        audiowrite(name,y,Fs);
    end
    if (count == floor(numFiles/2))
        disp('Halfway through 9dB');
    end
end

parfor count = 1:numFiles
    [x, chars] = randSeq(randomInt(5,50),Fs,1);
    y = awgn(x,13);
    name = strcat(folderName,'/13dB/',chars,'.wav');
    if (exist(name,'file'))
    %    count = count - 1;
        continue;
    else
        audiowrite(name,y,Fs);
    end
    if (count == floor(numFiles/2))
        disp('Halfway through 13dB');
    end
end

parfor count = 1:numFiles
    [x, chars] = randSeq(randomInt(5,50),Fs,1);
    y = awgn(x,17);
    name = strcat(folderName,'/17dB/',chars,'.wav');
    if (exist(name,'file'))
    %    count = count - 1;
        continue;
    else
        audiowrite(name,y,Fs);
    end
    if (count == floor(numFiles/2))
        disp('Halfway through 17dB');
    end
end

disp('Done with the sixth power range');

% parfor count = 1:numFiles
%     [x, chars] = randSeq(randomInt(5,50),Fs,1);
%     y = awgn(x,30);
%     name = strcat(folderName,'/30dB/',chars,'.wav');
%     if (exist(name,'file'))
%     %    count = count - 1;
%         continue;
%     else
%         audiowrite(name,y,Fs);
%     end
%     if (count == floor(numFiles/2))
%         disp('Halfway through 30dB');
%     end
% end
% 
% 
% parfor count = 1:numFiles
%     [x, chars] = randSeq(randomInt(5,50),Fs,1);
%     y = awgn(x,40);
%     name = strcat(folderName,'/40dB/',chars,'.wav');
%     if (exist(name,'file'))
%     %    count = count - 1;
%         continue;
%     else
%         audiowrite(name,y,Fs);
%     end
%     if (count == floor(numFiles/2))
%         disp('Halfway through 40dB');
%     end
% end


% parfor count = 1:numFiles
%     [x, chars] = randSeq(randomInt(5,50),Fs,1);
%     y = awgn(x,50);
%     name = strcat(folderName,'/50dB/',chars,'.wav');
%     if (exist(name,'file'))
%     %    count = count - 1;
%         continue;
%     else
%         audiowrite(name,y,Fs);
%     end
%     if (count == floor(numFiles/2))
%         disp('Halfway through 50dB');
%     end
% end
% 
% 
% parfor count = 1:numFiles
%     [x, chars] = randSeq(randomInt(5,50),Fs,1);
%     y = awgn(x,60);
%     name = strcat(folderName,'/60dB/',chars,'.wav');
%     if (exist(name,'file'))
%     %    count = count - 1;
%         continue;
%     else
%         audiowrite(name,y,Fs);
%     end
%     if (count == floor(numFiles/2))
%         disp('Halfway through 60dB');
%     end
% end

disp('Done with the seventh power range');
disp(strcat('Time: ',num2str(toc),'seconds'));
