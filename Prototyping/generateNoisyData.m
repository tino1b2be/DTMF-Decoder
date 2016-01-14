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
    mkdir(folderName,'/5dB');
    mkdir(folderName,'/7dB');
    mkdir(folderName,'/8dB');
    mkdir(folderName,'/10dB');
    mkdir(folderName,'/12dB');
    mkdir(folderName,'/15dB');
    mkdir(folderName,'/20dB');
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
    if (~exist(strcat(folderName,'/5dB'),'dir'))
        mkdir(folderName,'/5dB');
    end
    if (~exist(strcat(folderName,'/7dB'),'dir'))
        mkdir(folderName,'/7dB');
    end
    if (~exist(strcat(folderName,'/8dB'),'dir'))
        mkdir(folderName,'/8dB');
    end
    if (~exist(strcat(folderName,'/10dB'),'dir'))
        mkdir(folderName,'/10dB');
    end
    if (~exist(strcat(folderName,'/12dB'),'dir'))
        mkdir(folderName,'/12dB');
    end
    if (~exist(strcat(folderName,'/15dB'),'dir'))
        mkdir(folderName,'/10dB');
    end
    if (~exist(strcat(folderName,'/20dB'),'dir'))
        mkdir(folderName,'/20dB');
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

parfor count = 1:numFiles
    [x, chars] = randSeq(randomInt(5,50),Fs,1);
    y = awgn(x,5);
    name = strcat(folderName,'/5dB/',chars,'.wav');
    if (exist(name,'file'))
    %    count = count - 1;
        continue;
    else
        audiowrite(name,y,Fs);
    end
    if (count == floor(numFiles/2))
        disp('Halfway through 5dB');
    end
end


parfor count = 1:numFiles
    [x, chars] = randSeq(randomInt(5,50),Fs,1);
    y = awgn(x,5);
    name = strcat(folderName,'/5dB/',chars,'.wav');
    if (exist(name,'file'))
    %    count = count - 1;
        continue;
    else
        audiowrite(name,y,Fs);
    end
    if (count == floor(numFiles/2))
        disp('Halfway through 5dB');
    end
end

parfor count = 1:numFiles
    [x, chars] = randSeq(randomInt(5,50),Fs,1);
    y = awgn(x,7);
    name = strcat(folderName,'/7dB/',chars,'.wav');
    if (exist(name,'file'))
    %    count = count - 1;
        continue;
    else
        audiowrite(name,y,Fs);
    end
    if (count == floor(numFiles/2))
        disp('Halfway through 7dB');
    end
end

parfor count = 1:numFiles
    [x, chars] = randSeq(randomInt(5,50),Fs,1);
    y = awgn(x,8);
    name = strcat(folderName,'/8dB/',chars,'.wav');
    if (exist(name,'file'))
    %    count = count - 1;
        continue;
    else
        audiowrite(name,y,Fs);
    end
    if (count == floor(numFiles/2))
        disp('Halfway through 8dB');
    end
end

parfor count = 1:numFiles
    [x, chars] = randSeq(randomInt(5,50),Fs,1);
    y = awgn(x,10);
    name = strcat(folderName,'/10dB/',chars,'.wav');
    if (exist(name,'file'))
    %    count = count - 1;
        continue;
    else
        audiowrite(name,y,Fs);
    end
    if (count == floor(numFiles/2))
        disp('Halfway through 10dB');
    end
end

parfor count = 1:numFiles
    [x, chars] = randSeq(randomInt(5,50),Fs,1);
    y = awgn(x,10);
    name = strcat(folderName,'/10dB/',chars,'.wav');
    if (exist(name,'file'))
    %    count = count - 1;
        continue;
    else
        audiowrite(name,y,Fs);
    end
    if (count == floor(numFiles/2))
        disp('Halfway through 10dB');
    end
end

parfor count = 1:numFiles
    [x, chars] = randSeq(randomInt(5,50),Fs,1);
    y = awgn(x,12);
    name = strcat(folderName,'/12dB/',chars,'.wav');
    if (exist(name,'file'))
    %    count = count - 1;
        continue;
    else
        audiowrite(name,y,Fs);
    end
    if (count == floor(numFiles/2))
        disp('Halfway through 12dB');
    end
end

parfor count = 1:numFiles
    [x, chars] = randSeq(randomInt(5,50),Fs,1);
    y = awgn(x,15);
    name = strcat(folderName,'/15dB/',chars,'.wav');
    if (exist(name,'file'))
    %    count = count - 1;
        continue;
    else
        audiowrite(name,y,Fs);
    end
    if (count == floor(numFiles/2))
        disp('Halfway through 15dB');
    end
end

parfor count = 1:numFiles
    [x, chars] = randSeq(randomInt(5,50),Fs,1);
    y = awgn(x,20);
    name = strcat(folderName,'/20dB/',chars,'.wav');
    if (exist(name,'file'))
    %    count = count - 1;
        continue;
    else
        audiowrite(name,y,Fs);
    end
    if (count == floor(numFiles/2))
        disp('Halfway through 20dB');
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
