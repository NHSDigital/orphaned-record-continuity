import {
  extractDependencyUpdatesFromList,
  isUpdate,
  fromString,
  getAllUpdates
} from '../parse-dependency-updates';

const multipleUpdatesRequired = [
  '/usr/local/Cellar/node/13.10.1/bin/node',
  '/Users/someone/git/prm-deductions-component-template/check_for_updates.js',
  '/Users/someone/git/prm-deductions-component-template:uuid@3.4.0:uuid@7.0.2:uuid@7.0.2',
  '/Users/someone/git/prm-deductions-component-template:ppc@3.4.0:ppc@5.0.2:ppc@7.0.2'
];

const testData = [
  '/usr/local/Cellar/node/13.10.1/bin/node',
  '/Users/someone/git/prm-deductions-component-template/check_for_updates.js',
  '/Users/someone/git/prm-deductions-component-template:uuid@3.4.0:uuid@7.0.2:uuid@7.0.2'
];

const noUpdatesRequired = [
  '/usr/local/Cellar/node/13.10.1/bin/node',
  '/Users/someone/git/prm-deductions-component-template/check_for_updates.js'
];

describe('parse_dependency_updates.js', () => {
  describe('extractDependencyUpdatesFromList', () => {
    it('should filter the list to just dependency updates', () => {
      expect(extractDependencyUpdatesFromList(testData)).toEqual([
        '/Users/someone/git/prm-deductions-component-template:uuid@3.4.0:uuid@7.0.2:uuid@7.0.2'
      ]);
    });

    it('should return empty string when no updates are required', () => {
      expect(extractDependencyUpdatesFromList(noUpdatesRequired)).toEqual([]);
    });
  });

  describe('isUpdate', () => {
    it('should return true if the string contains an update', () => {
      expect(
        isUpdate(
          '/Users/someone/git/prm-deductions-component-template:uuid@3.4.0:uuid@7.0.2:uuid@7.0.2'
        )
      ).toBe(true);
    });

    it('should return false if the string does not contain an update', () => {
      expect(isUpdate('/usr/local/Cellar/node/13.10.1/bin/node')).toBe(false);
    });
  });

  describe('fromString', () => {
    it('should convert the update string to an object', () => {
      const updateString = fromString(
        '/Users/someone/git/prm-deductions-component-template:uuid@3.4.0:uuid@7.0.2:uuid@7.0.2'
      );
      expect(updateString).toMatchObject({
        currentVersion: '7.0.2',
        latestVersion: '7.0.2',
        package: 'uuid',
        wantedVersion: '3.4.0'
      });
    });
  });

  describe('getAllUpdates', () => {
    it('should return an array of objects', () => {
      const getAll = getAllUpdates(multipleUpdatesRequired);
      expect(getAll[0]).toMatchObject({
        currentVersion: '7.0.2',
        latestVersion: '7.0.2',
        package: 'uuid',
        wantedVersion: '3.4.0'
      });
    });
  });
});
